package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ahmed.zaghlolplayer.tv.Channel
import com.ahmed.zaghlolplayer.tv.PlaylistService
import com.ahmed.zaghlolplayer.tv.R
import com.ahmed.zaghlolplayer.tv.VodItem

@Composable
fun ZLogo(size: Dp = 44.dp) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher),
        contentDescription = "Zaghlol Player",
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp)),
        contentScale = ContentScale.Crop
    )
}

/** Gradient poster tile — same look as Windows v2.0.2/v2.0.3 (gradient + title,
 *  real cover fades in on top when available). */
@Composable
fun PosterTile(
    name: String,
    badge: String,
    cover: String,
    resume: Float?,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hue = PlaylistService.hueOf(name)
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color.hsl(hue, 0.62f, 0.40f),
            Color.hsl((hue + 45f) % 360f, 0.58f, 0.20f)
        )
    )
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .tvClickable(onClick = onClick)
    ) {
        if (cover.isNotEmpty()) {
            AsyncImage(
                model = cover,
                contentDescription = name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            // Dark scrim so the title stays readable over a real cover.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000)),
                            startY = 300f
                        )
                    )
            )
        }
        // Big monogram (first two letters) — the fallback visual.
        val monogram = name.trim().take(2)
        Text(
            text = monogram,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center)
        )
        if (isFavorite) {
            Text(
                text = "★",
                color = ZaghlolColors.Gold,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(ZaghlolColors.Accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (resume != null && resume > 0.02f && resume < 0.95f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(resume)
                        .height(4.dp)
                        .background(ZaghlolColors.Accent)
                )
            }
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    active: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (active) ZaghlolColors.AccentSoft else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .tvClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(ZaghlolColors.Live, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = "LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (channel.logo.isNotEmpty()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZaghlolColors.Panel3),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZaghlolColors.Panel3),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.trim().take(1),
                    color = ZaghlolColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = channel.name,
            color = ZaghlolColors.Text,
            fontSize = 19.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isFavorite) {
            Text(text = "★", color = ZaghlolColors.Gold, fontSize = 18.sp)
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    count: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (active) ZaghlolColors.Accent else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .tvClickable(onClick = onClick)
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            color = if (active) Color.White else ZaghlolColors.Muted,
            fontSize = 16.sp
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) ZaghlolColors.Accent else ZaghlolColors.Panel3,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .tvClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) ZaghlolColors.Accent else ZaghlolColors.Panel3,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .tvClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .border(1.dp, ZaghlolColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .tvClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = ZaghlolColors.Text, fontSize = 17.sp)
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .border(
                2.dp,
                if (focused) ZaghlolColors.Accent else ZaghlolColors.Border,
                RoundedCornerShape(12.dp)
            )
            .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            textStyle = TextStyle(color = ZaghlolColors.Text, fontSize = 18.sp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, color = ZaghlolColors.Dim, fontSize = 18.sp)
                }
                inner()
            }
        )
    }
}

@Composable
fun ErrorText(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = Color(0xFFFCA5A5),
            fontSize = 17.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun CountBadge(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(ZaghlolColors.Accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun EmptyState(big: String, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Text(text = big, fontSize = 48.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = ZaghlolColors.Muted,
            fontSize = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun movieBadge(vod: VodItem): String {
    return listOfNotNull(
        vod.year.takeIf { it.isNotEmpty() },
        vod.rating.takeIf { it.isNotEmpty() }?.let { "⭐ $it" },
        vod.categoryName.takeIf { it.isNotEmpty() }
    ).joinToString(" • ")
}
