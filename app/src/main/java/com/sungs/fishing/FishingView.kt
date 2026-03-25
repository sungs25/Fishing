package com.sungs.fishing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 낚시
 *
 * 평화로운 수면 위에 고요한 말들이 떠다닌다.
 * 터치하면 미끼를 던진 것 — 말들이 물고기처럼 달려든다.
 * 공격적으로 변하며 터치 지점으로 몰려든다.
 * 미끼가 다하면 흩어지고, 다시 평화가 온다.
 */
class FishingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── 단어 단계 ──
    // 평화 → 관심 → 공격 → 광기
    private val calmWords = listOf(
        "귀여워", "so cute", "かわいい", "可爱", "süß", "qué lindo",
        "파이팅", "wholesome", "お疲れ様", "加油", "echt toll", "ánimo",
        "좋은 하루", "love this", "いいね", "太棒了", "danke dir", "me encanta"
    )

    private val curiousWords = listOf(
        "뭐임?", "wait what", "は？", "真的假的", "was?", "¿qué?",
        "주작?", "sauce?", "ソースは？", "吃瓜", "Quelle?", "contexto?",
        "누구", "fr?", "マジで？", "蹲", "echt jetzt?", "wut"
    )

    private val aggressiveWords = listOf(
        "어그로", "cringe", "うざ", "杠精", "halt's Maul", "cállate",
        "관종", "stfu", "キモい", "恶心", "Müll", "basura",
        "노답", "touch grass", "構ってちゃん", "闭嘴", "nerv nicht", "tóxico"
    )

    private val frenzyWords = listOf(
        "나락가라", "cancelled", "炎上しろ", "封杀", "brenn", "funado",
        "죽어", "die", "死ね", "去死", "stirb", "muérete",
        "사회악", "scum", "晒せ", "网暴", "Abschaum", "escoria"
    )

    // ── 물고기(단어) ──
    data class WordFish(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var baseSpeed: Float,
        var wanderAngle: Float,         // 평화로울 때 유영 방향
        var wanderTimer: Float,         // 방향 바꾸는 타이머
        var wordIndex: Int,
        var size: Float,
        var alpha: Int,
        var state: FishState = FishState.CALM,
        var frenzyLevel: Float = 0f     // 개별 흥분도
    )

    enum class FishState { CALM, RUSHING, FEEDING, DISPERSING }

    // ── 물결 ──
    data class Wave(
        var offset: Float,
        var amplitude: Float,
        var frequency: Float,
        var speed: Float,
        var y: Float,
        var alpha: Int
    )

    // ── 잔물결(터치 시) ──
    data class Ripple(
        var x: Float,
        var y: Float,
        var radius: Float,
        var maxRadius: Float,
        var alpha: Int
    )

    private val fishes = mutableListOf<WordFish>()
    private val waves = mutableListOf<Wave>()
    private val ripples = mutableListOf<Ripple>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val bgPaint = Paint()

    private var screenW = 0f
    private var screenH = 0f

    // ── 미끼 상태 ──
    private var baitX = 0f
    private var baitY = 0f
    private var isBaitDropped = false
    private var baitRemaining = 0f          // 미끼 잔량 (0 ~ 100)
    private val baitMaxAmount = 100f
    private val baitConsumeRate = 0.15f     // 물고기가 미끼를 먹는 속도
    private val baitDecayRate = 0.05f       // 미끼 자연 소멸 속도

    // ── 전체 분위기 ──
    private var globalAggression = 0f       // 0(평화) ~ 1(광기)
    private var frameCount = 0L

    // ── 색상 ──
    private val calmBgColor = Color.rgb(15, 30, 60)         // 깊은 남색
    private val frenzyBgColor = Color.rgb(40, 10, 10)       // 어두운 핏빛
    private val calmWaterColor = Color.rgb(60, 100, 160)    // 잔잔한 파랑
    private val frenzyWaterColor = Color.rgb(150, 40, 40)   // 붉은 물

    private val calmTextColor = Color.rgb(150, 180, 220)    // 연한 하늘
    private val frenzyTextColor = Color.rgb(255, 50, 30)    // 강렬한 빨강

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()

        if (fishes.isEmpty()) {
            // 물고기 생성
            repeat(18) { spawnFish() }
            // 물결 레이어 생성
            repeat(5) { i ->
                waves.add(Wave(
                    offset = Random.nextFloat() * 1000f,
                    amplitude = Random.nextFloat() * 8f + 3f,
                    frequency = Random.nextFloat() * 0.008f + 0.003f,
                    speed = Random.nextFloat() * 0.3f + 0.1f,
                    y = screenH * (0.15f + i * 0.17f),
                    alpha = 40 + i * 15
                ))
            }
        }
    }

    private fun spawnFish() {
        val speed = Random.nextFloat() * 0.8f + 0.3f
        fishes.add(WordFish(
            x = Random.nextFloat() * screenW.coerceAtLeast(1f),
            y = Random.nextFloat() * screenH.coerceAtLeast(1f),
            vx = (Random.nextFloat() - 0.5f) * speed,
            vy = (Random.nextFloat() - 0.5f) * speed * 0.5f,
            baseSpeed = speed,
            wanderAngle = Random.nextFloat() * 360f,
            wanderTimer = Random.nextFloat() * 200f,
            wordIndex = Random.nextInt(calmWords.size),
            size = Random.nextFloat() * 18f + 32f,
            alpha = Random.nextInt(60) + 140
        ))
    }

    // ── 매 프레임 ──
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameCount++

        updateBait()
        updateFishes()
        updateRipples()

        drawBackground(canvas)
        drawWaves(canvas)
        drawRipples(canvas)
        drawFishes(canvas)

        postInvalidateOnAnimation()
    }

    // ── 미끼 업데이트 ──
    private fun updateBait() {
        if (isBaitDropped) {
            // 미끼가 서서히 소멸
            baitRemaining = (baitRemaining - baitDecayRate).coerceAtLeast(0f)

            // 미끼 다 떨어지면
            if (baitRemaining <= 0f) {
                isBaitDropped = false
                // 모든 물고기 흩어지기 시작
                fishes.forEach { it.state = FishState.DISPERSING }
            }
        }

        // 전체 분위기: 미끼가 있으면 상승, 없으면 하강
        globalAggression = if (isBaitDropped) {
            (globalAggression + 0.005f).coerceAtMost(1f)
        } else {
            (globalAggression - 0.003f).coerceAtLeast(0f)
        }
    }

    // ── 물고기(단어) 움직임 ──
    private fun updateFishes() {
        for (fish in fishes) {
            when (fish.state) {
                FishState.CALM -> updateCalmFish(fish)
                FishState.RUSHING -> updateRushingFish(fish)
                FishState.FEEDING -> updateFeedingFish(fish)
                FishState.DISPERSING -> updateDispersingFish(fish)
            }

            // 화면 경계 처리 (부드러운 래핑)
            if (fish.x < -120f) fish.x = screenW + 60f
            if (fish.x > screenW + 120f) fish.x = -60f
            if (fish.y < -80f) fish.y = screenH + 40f
            if (fish.y > screenH + 80f) fish.y = -40f
        }
    }

    // 평화롭게 유영
    private fun updateCalmFish(fish: WordFish) {
        fish.wanderTimer -= 1f
        if (fish.wanderTimer <= 0f) {
            fish.wanderAngle += (Random.nextFloat() - 0.5f) * 60f
            fish.wanderTimer = Random.nextFloat() * 150f + 50f
        }

        val rad = Math.toRadians(fish.wanderAngle.toDouble())
        fish.vx += (cos(rad).toFloat() * 0.02f)
        fish.vy += (sin(rad).toFloat() * 0.01f)

        // 속도 제한 (느긋하게)
        val speed = hypot(fish.vx, fish.vy)
        if (speed > fish.baseSpeed) {
            fish.vx *= 0.98f
            fish.vy *= 0.98f
        }

        fish.x += fish.vx
        fish.y += fish.vy

        // 흥분도 감소
        fish.frenzyLevel = (fish.frenzyLevel - 0.005f).coerceAtLeast(0f)
    }

    // 미끼를 향해 돌진
    private fun updateRushingFish(fish: WordFish) {
        val dx = baitX - fish.x
        val dy = baitY - fish.y
        val dist = hypot(dx, dy).coerceAtLeast(1f)

        // 미끼 방향으로 가속
        val accel = 0.15f + fish.frenzyLevel * 0.1f
        fish.vx += (dx / dist) * accel
        fish.vy += (dy / dist) * accel

        // 속도 제한 (빠르게)
        val maxSpeed = 4f + fish.frenzyLevel * 3f
        val speed = hypot(fish.vx, fish.vy)
        if (speed > maxSpeed) {
            fish.vx = (fish.vx / speed) * maxSpeed
            fish.vy = (fish.vy / speed) * maxSpeed
        }

        fish.x += fish.vx
        fish.y += fish.vy

        // 흥분도 상승
        fish.frenzyLevel = (fish.frenzyLevel + 0.01f).coerceAtMost(1f)

        // 미끼 근처에 도달하면 먹기 상태
        if (dist < 80f) {
            fish.state = FishState.FEEDING
        }
    }

    // 미끼 주변에서 광란
    private fun updateFeedingFish(fish: WordFish) {
        val dx = baitX - fish.x
        val dy = baitY - fish.y
        val dist = hypot(dx, dy).coerceAtLeast(1f)

        // 미끼 주변을 빙글빙글 + 랜덤 요동
        val circleForce = 0.1f
        fish.vx += (-dy / dist) * circleForce + (Random.nextFloat() - 0.5f) * 1.5f
        fish.vy += (dx / dist) * circleForce + (Random.nextFloat() - 0.5f) * 1.5f

        // 너무 멀어지면 다시 끌어당김
        if (dist > 100f) {
            fish.vx += (dx / dist) * 0.3f
            fish.vy += (dy / dist) * 0.3f
        }

        // 감속
        fish.vx *= 0.95f
        fish.vy *= 0.95f

        fish.x += fish.vx
        fish.y += fish.vy

        // 미끼 소모
        baitRemaining = (baitRemaining - baitConsumeRate).coerceAtLeast(0f)

        // 흥분도 최대
        fish.frenzyLevel = (fish.frenzyLevel + 0.02f).coerceAtMost(1f)

        // 미끼 없으면 흩어지기
        if (!isBaitDropped) {
            fish.state = FishState.DISPERSING
        }
    }

    // 흩어지며 진정
    private fun updateDispersingFish(fish: WordFish) {
        // 서서히 감속하며 다시 유영 모드로
        fish.vx *= 0.97f
        fish.vy *= 0.97f
        fish.x += fish.vx
        fish.y += fish.vy

        fish.frenzyLevel = (fish.frenzyLevel - 0.008f).coerceAtLeast(0f)

        if (fish.frenzyLevel <= 0.05f) {
            fish.state = FishState.CALM
            fish.wanderAngle = Random.nextFloat() * 360f
        }
    }

    // ── 잔물결 업데이트 ──
    private fun updateRipples() {
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            ripple.radius += 1.5f
            ripple.alpha = ((1f - ripple.radius / ripple.maxRadius) * 120).toInt()
            if (ripple.radius >= ripple.maxRadius) {
                iterator.remove()
            }
        }
    }

    // ── 그리기 ──
    private fun drawBackground(canvas: Canvas) {
        val t = globalAggression
        val r = lerp(Color.red(calmBgColor), Color.red(frenzyBgColor), t)
        val g = lerp(Color.green(calmBgColor), Color.green(frenzyBgColor), t)
        val b = lerp(Color.blue(calmBgColor), Color.blue(frenzyBgColor), t)
        bgPaint.color = Color.rgb(r, g, b)
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)
    }

    private fun drawWaves(canvas: Canvas) {
        val t = globalAggression

        val r = lerp(Color.red(calmWaterColor), Color.red(frenzyWaterColor), t)
        val g = lerp(Color.green(calmWaterColor), Color.green(frenzyWaterColor), t)
        val b = lerp(Color.blue(calmWaterColor), Color.blue(frenzyWaterColor), t)

        for (wave in waves) {
            wave.offset += wave.speed + t * 0.5f  // 흥분 시 물결 빨라짐

            val amplitudeNow = wave.amplitude * (1f + t * 3f)  // 흥분 시 물결 커짐

            wavePaint.color = Color.rgb(r, g, b)
            wavePaint.alpha = wave.alpha
            wavePaint.strokeWidth = 1.5f + t * 1.5f

            val path = Path()
            path.moveTo(0f, wave.y)
            var px = 0f
            while (px <= screenW) {
                val py = wave.y + sin((px * wave.frequency + wave.offset).toDouble()).toFloat() * amplitudeNow
                path.lineTo(px, py)
                px += 4f
            }
            canvas.drawPath(path, wavePaint)
        }
    }

    private fun drawRipples(canvas: Canvas) {
        for (ripple in ripples) {
            ripplePaint.alpha = ripple.alpha.coerceIn(0, 255)
            ripplePaint.color = Color.WHITE
            canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
        }
    }

    private fun drawFishes(canvas: Canvas) {
        for (fish in fishes) {
            val t = fish.frenzyLevel

            // 단어 선택
            val word = when {
                t < 0.2f -> calmWords[fish.wordIndex % calmWords.size]
                t < 0.5f -> curiousWords[fish.wordIndex % curiousWords.size]
                t < 0.8f -> aggressiveWords[fish.wordIndex % aggressiveWords.size]
                else -> frenzyWords[fish.wordIndex % frenzyWords.size]
            }

            // 색상
            val r = lerp(Color.red(calmTextColor), Color.red(frenzyTextColor), t)
            val g = lerp(Color.green(calmTextColor), Color.green(frenzyTextColor), t)
            val b = lerp(Color.blue(calmTextColor), Color.blue(frenzyTextColor), t)

            // 크기: 흥분할수록 커짐
            val sizeNow = fish.size * (1f + t * 0.6f)

            // 떨림
            val shakeX = if (t > 0.5f) (Random.nextFloat() - 0.5f) * t * 8f else 0f
            val shakeY = if (t > 0.5f) (Random.nextFloat() - 0.5f) * t * 8f else 0f

            textPaint.color = Color.rgb(r, g, b)
            textPaint.textSize = sizeNow
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.alpha = fish.alpha
            textPaint.style = if (t > 0.5f) {
                textPaint.strokeWidth = t * 3f
                Paint.Style.FILL_AND_STROKE
            } else {
                textPaint.strokeWidth = 0f
                Paint.Style.FILL
            }

            canvas.drawText(word, fish.x + shakeX, fish.y + shakeY, textPaint)
        }
    }

    // ── 터치 = 미끼 던지기 ──

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dropBait(event.x, event.y)
                performClick()
            }
            MotionEvent.ACTION_MOVE -> {
                baitX = event.x
                baitY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            }
        }
        return true
    }

    private fun dropBait(x: Float, y: Float) {
        baitX = x
        baitY = y
        isBaitDropped = true
        baitRemaining = baitMaxAmount

        // 잔물결 생성
        repeat(3) { i ->
            ripples.add(Ripple(
                x = x,
                y = y,
                radius = i * 5f,
                maxRadius = 80f + i * 30f,
                alpha = 120
            ))
        }

        // 물고기들이 미끼를 향해 달려옴
        for (fish in fishes) {
            val dist = hypot(fish.x - x, fish.y - y)
            // 가까운 물고기부터 반응 (먼 물고기는 느리게)
            if (dist < screenW * 0.7f || Random.nextFloat() < 0.6f) {
                fish.state = FishState.RUSHING
            }
        }
    }

    // ── 유틸 ──
    private fun lerp(a: Int, b: Int, t: Float): Int {
        return (a + (b - a) * t.coerceIn(0f, 1f)).toInt()
    }
}