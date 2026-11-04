package ru.moireisy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Navy = Color(0xFF062653)
private val Blue = Color(0xFF0878E8)
private val Cyan = Color(0xFF16C5F4)

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 35, 77)
        window.navigationBarColor = android.graphics.Color.rgb(5, 35, 77)
        setContent {
            BrandingSplashScreen {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
private fun BrandingSplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2100)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Navy)
    ) {
        val compact = maxHeight < 700.dp
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF031A3B), Color(0xFF07539A), Color(0xFF9BC9EE))
                )
            )
            val mountains = Path().apply {
                moveTo(0f, h * .67f)
                lineTo(w * .16f, h * .56f)
                lineTo(w * .31f, h * .65f)
                lineTo(w * .48f, h * .54f)
                lineTo(w * .68f, h * .65f)
                lineTo(w * .83f, h * .57f)
                lineTo(w, h * .66f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(mountains, Color(0xFF183D69))
            val road = Path().apply {
                moveTo(w * .18f, h)
                lineTo(w * .49f, h * .73f)
                lineTo(w * .72f, h * .73f)
                lineTo(w, h)
                close()
            }
            drawPath(road, Color(0xFF172B45))
            drawLine(Color(0xFFEAF5FF), Offset(w * .49f, h * .98f), Offset(w * .57f, h * .76f), strokeWidth = 5f)
            drawLine(Color(0xFFEAF5FF), Offset(w * .75f, h * .98f), Offset(w * .67f, h * .76f), strokeWidth = 5f)

            // Curtain-side semi-truck silhouette on the lower-right.
            val trailer = Path().apply {
                moveTo(w * .47f, h * .61f)
                lineTo(w * .82f, h * .57f)
                lineTo(w * .82f, h * .76f)
                lineTo(w * .47f, h * .79f)
                close()
            }
            drawPath(trailer, Brush.linearGradient(listOf(Color(0xFFF8FCFF), Color(0xFF9FB7CD))))
            val cab = Path().apply {
                moveTo(w * .80f, h * .63f)
                lineTo(w * .91f, h * .62f)
                lineTo(w * .99f, h * .70f)
                lineTo(w * .99f, h * .81f)
                lineTo(w * .79f, h * .81f)
                close()
            }
            drawPath(cab, Brush.linearGradient(listOf(Color.White, Color(0xFF7898B5))))
            val windshield = Path().apply {
                moveTo(w * .88f, h * .645f)
                lineTo(w * .93f, h * .645f)
                lineTo(w * .97f, h * .70f)
                lineTo(w * .86f, h * .70f)
                close()
            }
            drawPath(windshield, Color(0xFF102B4C))
            drawLine(Color(0xFF45617D), Offset(w * .49f, h * .62f), Offset(w * .82f, h * .59f), strokeWidth = 3f)
            drawCircle(Color(0xFF101D2E), 13f, Offset(w * .61f, h * .79f))
            drawCircle(Color(0xFF101D2E), 13f, Offset(w * .78f, h * .79f))
            drawCircle(Color(0xFF101D2E), 15f, Offset(w * .91f, h * .81f))
            drawCircle(Cyan, 5f, Offset(w * .965f, h * .75f))
            drawCircle(Cyan, 5f, Offset(w * .83f, h * .75f))
            drawLine(Cyan, Offset(w * .83f, h * .75f), Offset(w * .97f, h * .75f), strokeWidth = 4f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = if (compact) 18.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.18f))
            BrandMark(Modifier.size(if (compact) 112.dp else 142.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Мои Рейсы",
                color = Color.White,
                fontSize = if (compact) 34.sp else 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            )
            Box(
                Modifier.padding(top = 8.dp).width(180.dp).height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Cyan, Blue)))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Учёт поездок  •  Маршруты\nРасходы  •  Заработок",
                color = Color(0xFFEAF4FF),
                fontSize = if (compact) 14.sp else 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Text("Загрузка...", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth(.72f).height(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x668DB7E3))
            ) {
                Box(
                    Modifier.fillMaxWidth(.72f).fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Cyan, Blue)))
                )
            }
            Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val arc = Path().apply {
            moveTo(w * .08f, h * .66f)
            cubicTo(w * .25f, h * .12f, w * .76f, h * .08f, w * .94f, h * .52f)
        }
        drawPath(arc, Cyan, style = Stroke(width = w * .035f))
        val trailer = Path().apply {
            moveTo(w * .12f, h * .43f)
            lineTo(w * .58f, h * .27f)
            lineTo(w * .58f, h * .62f)
            lineTo(w * .12f, h * .70f)
            close()
        }
        drawPath(trailer, Color.White)
        val cab = Path().apply {
            moveTo(w * .58f, h * .42f)
            lineTo(w * .78f, h * .39f)
            lineTo(w * .91f, h * .52f)
            lineTo(w * .91f, h * .69f)
            lineTo(w * .57f, h * .69f)
            close()
        }
        drawPath(cab, Color.White)
        val windshield = Path().apply {
            moveTo(w * .72f, h * .43f)
            lineTo(w * .80f, h * .43f)
            lineTo(w * .87f, h * .52f)
            lineTo(w * .70f, h * .52f)
            close()
        }
        drawPath(windshield, Navy)
        drawCircle(Navy, w * .055f, Offset(w * .32f, h * .70f))
        drawCircle(Navy, w * .055f, Offset(w * .66f, h * .70f))
        drawCircle(Navy, w * .055f, Offset(w * .84f, h * .70f))
        drawCircle(Blue, w * .12f, Offset(w * .52f, h * .16f))
        drawCircle(Color.White, w * .055f, Offset(w * .52f, h * .16f))
        val pinTip = Path().apply {
            moveTo(w * .42f, h * .20f)
            lineTo(w * .62f, h * .20f)
            lineTo(w * .52f, h * .39f)
            close()
        }
        drawPath(pinTip, Blue)
        drawLine(Color.White, Offset(w * .12f, h * .78f), Offset(w * .88f, h * .78f), strokeWidth = w * .035f)
        drawLine(Cyan, Offset(w * .78f, h * .63f), Offset(w * .90f, h * .63f), strokeWidth = w * .045f)
    }
}
