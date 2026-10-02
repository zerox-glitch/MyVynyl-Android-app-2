package com.vynyl.record.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vynyl.record.audio.CRACKLES
import com.vynyl.record.audio.MOODS
import com.vynyl.record.audio.MUSIC
import com.vynyl.record.audio.PRESETS
import com.vynyl.record.audio.STYLES
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.sansStyle

private class GItem(val name: String, val text: String)
private class GSection(val id: String, val title: String, val intro: String, val tip: String? = null, val items: List<GItem> = emptyList())

/** Plain-language help for everyone — big type, short sentences, no jargon. Same wording as web Guide.tsx. */
private val GUIDE by lazy { listOf(
    GSection("steps", "Making a record, step by step", "There are five simple steps. You can go back to any of them using the Back button at the bottom. Tap Record at the bottom of the screen at any time to go straight to recording — nothing you have done is lost.", items = listOf(
        GItem("1. Capture", "Tap the big gold button and speak. Tap it again to stop. You can also choose a recording already on your device."),
        GItem("2. Dedication", "Write who the record is for, who it is from, and a short message. These words are printed on the record label."),
        GItem("3. Character", "Choose how your record sounds — the mood, the sound style, the crackle and the background music."),
        GItem("4. Appearance", "Choose the colour of your record."),
        GItem("5. Press", "Tap Press and wait a few seconds. Your record is saved on your shelf. Then you can play it, or go back and make changes."),
    )),
    GSection("moods", "Moods — the easy choice", "A mood is a ready-made recipe. One tap chooses the sound style, the crackle and the music for you, all at once. If you are not sure what to pick, just choose a mood.",
        "Tap ▶ on any mood to hear it before you choose.", MOODS.map { GItem(it.name, it.blurb) }),
    GSection("style", "Sound style", "This changes how your voice sounds. Some styles keep your voice clear and modern. Others make it sound old — like a family record from long ago, or a radio broadcast.",
        "The little bars show Warmth (how soft and cosy), Age (how old it sounds) and Texture (how much grain and noise).", PRESETS.map { GItem(it.name, it.blurb) }),
    GSection("crackle", "Crackle", "Real records make small ticks, pops and a soft hiss as they spin. This is called crackle. It makes your record feel real and old-fashioned.",
        "Want no crackle at all? Choose “Silent Surface”.", CRACKLES.map { GItem(it.name, it.blurb) }),
    GSection("music", "Background music", "Soft music that plays quietly underneath your voice. Your voice always stays on top, so it is easy to hear.",
        "Want only your voice? Choose “No background”. Music marked ♥ is extra romantic.", MUSIC.map { GItem(if (it.romantic) "♥ ${it.name}" else it.name, it.blurb) }),
    GSection("sliders", "The sliders", "Slide left for less, right for more.", items = listOf(
        GItem("Overall volume", "How loud the whole record is."),
        GItem("Music volume", "How loud the background music is. Slide all the way left to turn it off."),
        GItem("Character strength", "How strong the sound style is. Left is clean, right is very old-sounding."),
        GItem("Crackle volume", "How loud the ticks and hiss are. Slide all the way left to turn them off."),
    )),
    GSection("colour", "Record colour", "In the Appearance step you choose the colour of the vinyl and its label. It does not change the sound — only the look.", items = STYLES.map { GItem(it.name, "") }),
    GSection("play", "Playing your record", "Your record plays on a turntable, just like the real thing.", items = listOf(
        GItem("Play", "Tap play. The arm moves over and the needle drops onto the record — you will hear a soft thump, then your record."),
        GItem("Look around", "Drag with one finger to turn the turntable. Pinch with two fingers to move closer or further away."),
        GItem("Add a photo", "Put a photo of a loved one in the middle of the record."),
        GItem("Gold nameplate", "Shows both your names in gold on the turntable."),
        GItem("Back to Studio", "Changed your mind? Go back and change the music, crackle or mood. Your voice is kept, so you do not need to record again."),
    )),
    GSection("shelf", "Your shelf", "Every record you make is kept on your shelf. Tap a record to play it. You can share it as sound, or as a video of the record spinning. Your records stay on this device unless you share them."),
    GSection("pro", "Free and Pro", "You can make records for free. Some extras — more sounds, colours, longer recordings, the gold nameplate and changing a record more than 3 times after pressing — need Vynyl Pro. Anything marked PRO is part of it."),
) }

@Composable
fun Guide(onClose: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>("steps") }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(V.obsidian).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GUIDE", style = decoStyle(11, V.amberBright))
                    Text("How Vynyl works", style = displayStyle(30))
                }
                Text("Close", style = sansStyle(16), modifier = Modifier.clip(CircleShape).border(1.dp, V.cream.copy(alpha = 0.2f), CircleShape)
                    .clickable { onClose() }.padding(horizontal = 20.dp, vertical = 12.dp))
            }
            HorizontalDivider(color = V.cream.copy(alpha = 0.1f))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(GUIDE, key = { it.id }) { s ->
                    val on = open == s.id
                    val shape = RoundedCornerShape(16.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(if (on) V.stone else V.stone.copy(alpha = 0.6f))
                        .border(1.dp, if (on) V.amber.copy(alpha = 0.5f) else V.cream.copy(alpha = 0.1f), shape)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { open = if (on) null else s.id }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(s.title, style = sansStyle(18, weight = 500), modifier = Modifier.weight(1f))
                            Text("+", style = sansStyle(24, V.amberBright), modifier = Modifier.rotate(if (on) 45f else 0f))
                        }
                        if (on) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                            Text(s.intro, style = sansStyle(17, V.cream.copy(alpha = 0.9f)).copy(lineHeight = 27.sp))
                            s.tip?.let { Text("💡 $it", style = sansStyle(16, V.amberBright).copy(lineHeight = 25.sp),
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(V.amber.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 12.dp)) }
                            s.items.forEachIndexed { i, it ->
                                if (i > 0) HorizontalDivider(color = V.cream.copy(alpha = 0.1f))
                                Column(Modifier.padding(vertical = 12.dp).padding(top = if (i == 0) 4.dp else 0.dp)) {
                                    Text(it.name, style = sansStyle(16, weight = 600))
                                    if (it.text.isNotEmpty()) Text(it.text, style = sansStyle(16, V.muted).copy(lineHeight = 25.sp), modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3))).clickable { onClose() },
                        contentAlignment = Alignment.Center) { Text("GOT IT", style = decoStyle(14, V.obsidian)) }
                }
            }
        }
    }
}
