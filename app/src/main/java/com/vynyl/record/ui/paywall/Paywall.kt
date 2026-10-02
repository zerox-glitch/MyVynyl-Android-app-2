package com.vynyl.record.ui.paywall

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vynyl.record.pro.*
import com.vynyl.record.ui.theme.*

private val goldV = Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3))


/** Draws an SVG path (viewBox 0..vb) filled with `tint`. */
@Composable
private fun SvgIcon(d: String, vb: Float, size: Int, tint: Color) {
    val p = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width / vb
        scale(s, s, pivot = Offset.Zero) { drawPath(p, tint) }
    }
}

private const val LOCK = "M3.5 5V3.8a2.5 2.5 0 0 1 5 0V5h.4c.6 0 1.1.5 1.1 1.1v3.8c0 .6-.5 1.1-1.1 1.1H3.1C2.5 11 2 10.5 2 9.9V6.1C2 5.5 2.5 5 3.1 5h.4Zm1.2 0h2.6V3.8a1.3 1.3 0 0 0-2.6 0V5Z"
private const val CROWN = "M2 5.2 5.2 8 8 3l2.8 5L14 5.2 12.8 12H3.2L2 5.2ZM3.4 13h9.2v1.2H3.4z"

@Composable private fun Crown(tint: Color, size: Int = 14) = SvgIcon(CROWN, 16f, size, tint)

/** Small gold "PRO" tag shown on locked items. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(6.dp, CircleShape, ambientColor = V.amberBright, spotColor = V.amberBright)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(V.amberBright.copy(.28f), V.brass.copy(.18f))))
            .border(1.dp, V.amberBright.copy(.6f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SvgIcon(LOCK, 12f, 10, V.amberBright)
        Text("PRO", style = monoStyle(9, V.amberBright, 500).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.3f, androidx.compose.ui.unit.TextUnitType.Sp)))
    }
}

/** Header pill: "Go Pro" for free users, a gold "Pro" crest for subscribers. */
@Composable
fun ProButton(modifier: Modifier = Modifier) {
    val ent by Pro.entitlement.collectAsState()
    if (ent.pro) {
        Box(modifier.heightIn(min = 36.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(V.gold1, V.gold3))).padding(1.dp).clickable { Pro.openPaywall() }) {
            Row(Modifier.clip(CircleShape).background(V.obsidian).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Crown(V.amberBright); Text("PRO", style = decoStyle(11, V.amberBright))
            }
        }
    } else {
        Row(
            modifier.heightIn(min = 36.dp).clip(CircleShape).background(goldV).clickable { Pro.openPaywall() }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { Crown(V.obsidian); Text("GO PRO", style = decoStyle(11, V.obsidian)) }
    }
}

private val ROWS = listOf(
    Triple("Recording length", "${Free.MAX_SECONDS / 60} min", "${PRO_SECONDS / 60} min"),
    Triple("Records on your shelf", "${Free.MAX_RECORDS}", "Unlimited"),
    Triple("Characters", "${Free.presets.size}", "All"),
    Triple("Crackle styles", "${Free.crackles.size}", "All + levels"),
    Triple("Background music", "${Free.music.size - 1}", "All"),
    Triple("Wax colours", "${Free.styles.size}", "All"),
    Triple("Gold nameplate", "—", "✓"),
    Triple("WAV export", "Tagged", "Clean"),
)

/** Full-screen paywall / plan sheet, opened from anywhere via Pro.openPaywall(). */
@Composable
fun Paywall() {
    val open by Pro.paywall.collectAsState()
    val ent by Pro.entitlement.collectAsState()
    val prices by Pro.prices.collectAsState()
    var plan by remember { mutableStateOf(PlanId.Yearly) }
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var welcome by remember { mutableStateOf(false) }
    val activity = LocalContext.current as Activity
    val reason = open ?: return
    fun close() { Pro.closePaywall(); msg = ""; welcome = false }
    fun price(p: Plan) = prices[p.id] ?: p.price
    val chosen = PLANS.first { it.id == plan }

    Box(
        Modifier.fillMaxSize().background(V.obsidian)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(V.amberBright.copy(.28f), Color.Transparent), Offset(size.width / 2, 0f), size.width * .9f))
                drawRect(Brush.radialGradient(listOf(V.ruby.copy(.25f), Color.Transparent), Offset(size.width / 2, size.height), size.width * .7f))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, V.amberBright.copy(.7f), Color.Transparent)), size = androidx.compose.ui.geometry.Size(size.width, 1f))
            }
            .systemBarsPadding(),
    ) {
        if (welcome || (ent.pro && busy == null)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Disc(spin = true)
                Text((if (welcome) "Welcome to" else "You’re on").uppercase(), Modifier.padding(top = 32.dp), style = decoStyle(11, V.amberBright).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(3.3f, androidx.compose.ui.unit.TextUnitType.Sp)))
                Text(buildAnnotatedString { append("Vynyl "); withStyle(SpanStyle(color = V.amberBright, fontStyle = FontStyle.Italic)) { append("Pro") } }, Modifier.padding(top = 4.dp), style = displayStyle(48))
                val planLine = if (ent.plan == PlanId.Lifetime) "Lifetime access — every feature, forever." else "${PLANS.firstOrNull { it.id == ent.plan }?.name ?: "Pro"} plan."
                Text("$planLine Every mood, character, crackle and track is unlocked.", Modifier.widthIn(max = 256.dp).padding(top = 16.dp), style = sansStyle(14, V.muted), textAlign = TextAlign.Center)
                Box(Modifier.padding(top = 32.dp).widthIn(max = 256.dp).fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(goldV).clickable { close() }, contentAlignment = Alignment.Center) {
                    Text("Start pressing", style = sansStyle(15, V.obsidian, 600))
                }
                if (!welcome) Text("Manage or cancel your subscription in Google Play.", Modifier.padding(top = 24.dp), style = sansStyle(11, V.muted.copy(.8f)), textAlign = TextAlign.Center)
                Text(
                    "⚡ Test Mode: Relock Paywall",
                    Modifier.padding(top = 16.dp).clickable {
                        Pro.setProForTesting(false)
                        welcome = false
                    },
                    style = monoStyle(11, V.muted).copy(textDecoration = TextDecoration.Underline)
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Disc()
                        Text("VYNYL RECORD", Modifier.padding(top = 20.dp), style = decoStyle(11, V.amberBright).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(3.3f, androidx.compose.ui.unit.TextUnitType.Sp)))
                        Text(buildAnnotatedString { append("Go "); withStyle(SpanStyle(color = V.amberBright, fontStyle = FontStyle.Italic)) { append("Pro.") } }, style = displayStyle(44))
                        Text(reason.ifEmpty { "Press records the way they deserve — every sound, every colour, every minute." }, Modifier.widthIn(max = 288.dp).padding(top = 12.dp), style = sansStyle(14, V.muted), textAlign = TextAlign.Center)
                    }
                    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PERKS.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(V.amber.copy(.2f)).border(1.dp, V.amber.copy(.5f), CircleShape), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.size(12.dp)) {
                                        val u = size.width / 12f
                                        val path = Path().apply { moveTo(2.5f * u, 6.2f * u); lineTo(5f * u, 8.5f * u); lineTo(9.5f * u, 3.5f * u) }
                                        drawPath(path, V.amberBright, style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                                Column { Text(p.title, style = sansStyle(13, V.cream, 500)); Text(p.body, style = sansStyle(11, V.muted).copy(lineHeight = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))) }
                            }
                        }
                    }
                    val shape = RoundedCornerShape(16.dp)
                    Column(Modifier.padding(top = 24.dp).clip(shape).background(V.panel.copy(.7f)).border(1.dp, V.brass.copy(.25f), shape)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("COMPARE", Modifier.weight(1f), style = decoStyle(10, V.muted))
                            Text("FREE", Modifier.width(64.dp), style = decoStyle(10, V.muted), textAlign = TextAlign.Center)
                            Text("PRO", Modifier.width(80.dp), style = decoStyle(10, V.amberBright), textAlign = TextAlign.Center)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(V.brass.copy(.2f)))
                        ROWS.forEachIndexed { i, (k, f, p) ->
                            Row(Modifier.background(if (i % 2 == 1) V.cream.copy(.02f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(k, Modifier.weight(1f), style = sansStyle(12, V.cream.copy(.85f)))
                                Text(f, Modifier.width(64.dp), style = monoStyle(11, V.muted), textAlign = TextAlign.Center)
                                Text(p, Modifier.width(80.dp), style = monoStyle(11, V.amberBright), textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Column(Modifier.padding(top = 24.dp).selectableGroupCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PLANS.forEach { p -> PlanCard(p, price(p), plan == p.id) { plan = p.id } }
                    }
                }
                Column(Modifier.fillMaxWidth().background(V.stone.copy(.95f)).drawBehind { drawRect(V.brass.copy(.2f), size = androidx.compose.ui.geometry.Size(size.width, 1f)) }.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)) {
                    if (msg.isNotEmpty()) Text(msg, Modifier.fillMaxWidth().padding(bottom = 8.dp), style = sansStyle(12, V.err), textAlign = TextAlign.Center)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape).background(goldV).alpha(if (busy != null) .7f else 1f)
                            .clickable(enabled = busy == null) {
                                busy = "buy"; msg = ""
                                Pro.purchase(activity, plan) { ok ->
                                    busy = null
                                    if (ok) welcome = true else msg = "The purchase didn’t go through. You haven’t been charged."
                                }
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy == "buy") CircularProgressIndicator(Modifier.size(16.dp), color = V.obsidian, strokeWidth = 2.dp) else Crown(V.obsidian)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when { busy == "buy" -> "Confirming…"; plan == PlanId.Lifetime -> "Unlock Pro forever · ${price(chosen)}"; else -> "Continue · ${price(chosen)} ${chosen.per}" },
                            style = sansStyle(15, V.obsidian, 600),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (busy == "restore") "Checking…" else "Restore purchase",
                            Modifier.heightIn(min = 32.dp).clickable(enabled = busy == null) {
                                busy = "restore"; msg = ""
                                Pro.restore { ok -> activity.runOnUiThread { busy = null; if (ok) welcome = true else msg = "No Pro purchase found for this account." } }
                            }.wrapContentHeight(),
                            style = sansStyle(11, V.muted).copy(textDecoration = TextDecoration.Underline))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("Terms", style = sansStyle(11, V.muted)); Text("Privacy", style = sansStyle(11, V.muted)) }
                    }
                    Text(if (plan == PlanId.Lifetime) "One-time purchase." else "Renews automatically until cancelled in Google Play.",
                        Modifier.fillMaxWidth().padding(top = 6.dp), style = sansStyle(10, V.muted.copy(.7f)), textAlign = TextAlign.Center)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(V.amber.copy(.18f))
                                .border(1.dp, V.amberBright.copy(.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    Pro.setProForTesting(true)
                                    welcome = true
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("⚡ Test Mode: Unlock Pro Free", style = monoStyle(11, V.amberBright))
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp).size(40.dp).clip(CircleShape).background(V.obsidian.copy(.7f))
                .border(1.dp, V.brass.copy(.3f), CircleShape).clickable { close() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                val st = Stroke(2.dp.toPx() * .66f, cap = StrokeCap.Round)
                val u = size.width / 24f
                drawLine(V.cream.copy(.8f), Offset(6 * u, 6 * u), Offset(18 * u, 18 * u), st.width, StrokeCap.Round)
                drawLine(V.cream.copy(.8f), Offset(18 * u, 6 * u), Offset(6 * u, 18 * u), st.width, StrokeCap.Round)
            }
        }
    }
}

private fun Modifier.selectableGroupCompat() = this.selectableGroup()

@Composable
private fun PlanCard(p: Plan, price: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.padding(top = if (p.badge != null) 10.dp else 0.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (on) Brush.linearGradient(listOf(V.amberBright.copy(.2f), V.amberBright.copy(.04f))) else SolidColor(V.panel))
                .border(1.dp, if (on) V.amberBright else V.brass.copy(.25f), shape)
                .clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(20.dp).border(2.dp, if (on) V.amberBright else V.brass.copy(.5f), CircleShape), contentAlignment = Alignment.Center) {
                if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(V.amberBright))
            }
            Column(Modifier.weight(1f)) { Text(p.name, style = displayStyle(18)); p.note?.let { Text(it, style = sansStyle(11, V.muted)) } }
            Column(horizontalAlignment = Alignment.End) { Text(price, style = displayStyle(20)); Text(p.per, style = monoStyle(10, V.muted)) }
        }
        p.badge?.let {
            Text(it.uppercase(), Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = (-10).dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(V.gold1, Color(0xFFB8862E)))).padding(horizontal = 10.dp, vertical = 2.dp),
                style = monoStyle(9, V.obsidian, 500))
        }
    }
}

@Composable
private fun Disc(spin: Boolean = false) {
    val rot = if (spin) rememberInfiniteTransition(label = "disc").animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "r").value else 0f
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().blur(40.dp).clip(CircleShape).background(V.amberBright.copy(.25f)))
        Canvas(Modifier.fillMaxSize().rotate(rot).shadow(18.dp, CircleShape).clip(CircleShape)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF15110E))
            var rr = r
            while (rr > 0) { drawCircle(Color(0xFF2A221B), rr - 2.5.dp.toPx() / 2, style = Stroke(1.dp.toPx())); rr -= 3.5.dp.toPx() }
            drawCircle(Brush.sweepGradient(
                0f to Color.Transparent, .2f to Color.Transparent, .25f to V.cream.copy(.14f), .32f to Color.Transparent,
                .7f to Color.Transparent, .75f to V.cream.copy(.1f), .82f to Color.Transparent, 1f to Color.Transparent))
            drawCircle(V.amberBright.copy(.4f), r - .5f, style = Stroke(1f))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFBBF24), V.brass), center = Offset(15f * 2.75f, 13f * 2.75f), radius = 120f)), contentAlignment = Alignment.Center) {
            Crown(V.obsidian)
        }
    }
}
