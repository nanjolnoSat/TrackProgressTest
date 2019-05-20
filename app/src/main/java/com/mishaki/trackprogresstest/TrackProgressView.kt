package com.mishaki.trackprogresstest

import android.content.Context
import android.graphics.*
import android.support.v4.content.ContextCompat
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class TrackProgressView : View {
    private var circleAngle = 30f
    private var arcRadius = 0f
    private var lineWidth = 0f
    private var baseLineY = 0f

    private var circleRadius = 0f
    private var circleY = 0f
    private var pathBorderLeft = 0f
    private var pathBorderRight = 0f
    private var pathCircleTop = 0f
    private var pathCircleBottom = 0f
    private var textBaseLineOffset = 0f

    var quiverAngle = 2.5f
    var changeAngle = 0.5f
    private var currentQuiverAngle = 0f
    private var isLarge = true

    var maxJumpHeight = 3f
    private var currentJumpHeight = maxJumpHeight
    var changeHeight = 0.3f
    private var currentJump = 0f
    private var isTop = true

    private val totalPath = Path()
    private val totalPaint: Paint = Paint().also {
        it.style = Paint.Style.FILL
        it.isAntiAlias = true
    }
    private val progressPath = Path()
    private val progressPaint = Paint().also {
        it.style = Paint.Style.FILL
        it.isAntiAlias = true
    }

    var progressBitmap: Bitmap
    private val percentTextPaint = TextPaint().also {
        it.isAntiAlias = true
        it.textAlign = Paint.Align.CENTER
    }
    var textBaseLine = 0f
    private var percentBackgroundHalfWidth = 0f
    private var percentTextPaddingHorizontal = 2f

    var progress = 0
        set(progress) {
            if (progress > max) {
                return
            }
            field = progress
        }
    var max = 100
        set(max) {
            if (max < progress) {
                return
            }
            field = max
        }

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        var percentBackgroundResId = R.drawable.track_percent
        var percentBackgroundColor = 0xffff0000.toInt()
        arcRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, context.resources.displayMetrics)
        var isChangeMarkerColor = false
        var markerTextBaseLinePercent = 0.5f
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.TrackProgressView)

            circleAngle = array.getFloat(R.styleable.TrackProgressView_tpv_baseCircleAngle, circleAngle)
            baseLineY = array.getDimension(R.styleable.TrackProgressView_tpv_baseLineY, baseLineY)
            arcRadius = array.getDimension(R.styleable.TrackProgressView_tpv_progressLineHeight, arcRadius * 2f) / 2f
            quiverAngle = array.getFloat(R.styleable.TrackProgressView_tpv_quiverAngle, quiverAngle)
            changeAngle = array.getFloat(R.styleable.TrackProgressView_tpv_changeAngle, changeAngle)
            val totalColor = array.getColor(R.styleable.TrackProgressView_tpv_totalColor, 0xffcfcfcf.toInt())
            totalPaint.color = totalColor
            val progressColor = array.getColor(R.styleable.TrackProgressView_tpv_progressColor, 0xffff0000.toInt())
            progressPaint.color = progressColor
            progress = array.getInt(R.styleable.TrackProgressView_tpv_currentProgress, 0)
            max = array.getInt(R.styleable.TrackProgressView_tpv_maxProgress, max)
            percentBackgroundResId = array.getResourceId(R.styleable.TrackProgressView_tpv_markerRes, percentBackgroundResId)
            percentBackgroundColor = array.getColor(R.styleable.TrackProgressView_tpv_markerColor, percentBackgroundColor)
            isChangeMarkerColor = array.getBoolean(R.styleable.TrackProgressView_tpv_markerChangeColor, false)
            markerTextBaseLinePercent = array.getFloat(R.styleable.TrackProgressView_tpv_markerTextBaseLinePercent, markerTextBaseLinePercent)
            val textColor = array.getColor(R.styleable.TrackProgressView_tpv_textColor, 0xffffffff.toInt())
            percentTextPaint.color = textColor
            val textSize = array.getDimension(R.styleable.TrackProgressView_tpv_textSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15f, context.resources.displayMetrics))
            percentTextPaint.textSize = textSize
            percentTextPaddingHorizontal = array.getDimension(R.styleable.TrackProgressView_tpv_textHorizontalPadding, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics))
            maxJumpHeight = array.getFloat(R.styleable.TrackProgressView_tpv_maxJumpHeight, maxJumpHeight)
            changeHeight = array.getFloat(R.styleable.TrackProgressView_tpv_changeHeight, changeHeight)

            array.recycle()
        }
        val percentTextWidth = percentTextPaint.measureText("100%")
        if (paddingLeft <= 0 || paddingRight <= 0) {
            val DEFAULT_HORIZONTAL_PADDING = ((percentTextWidth + percentTextPaddingHorizontal * 2f) / 2f).toInt()
            setPadding(DEFAULT_HORIZONTAL_PADDING, 0, DEFAULT_HORIZONTAL_PADDING, 0)
        }

        textBaseLineOffset = calcTextBaseline()

        val trackPercentDrawable = ContextCompat.getDrawable(context, percentBackgroundResId)!!
        if (isChangeMarkerColor) {
            trackPercentDrawable.setColorFilter(percentBackgroundColor, PorterDuff.Mode.SRC_IN)
        }
        val bitmap = Bitmap.createBitmap(trackPercentDrawable.intrinsicWidth, trackPercentDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        trackPercentDrawable.setBounds(0, 0, trackPercentDrawable.intrinsicWidth, trackPercentDrawable.intrinsicHeight)
        trackPercentDrawable.draw(canvas)
        val matrix = Matrix()
        val scale = (percentTextWidth + percentTextPaddingHorizontal * 2f) / trackPercentDrawable.intrinsicWidth.toFloat()
        matrix.postScale(scale, scale)
        progressBitmap = Bitmap.createBitmap(bitmap, 0, 0, trackPercentDrawable.intrinsicWidth, trackPercentDrawable.intrinsicHeight, matrix, true)
        textBaseLine = progressBitmap.height * (1f - markerTextBaseLinePercent)
        percentBackgroundHalfWidth = progressBitmap.width.toFloat() / 2f

        pathCircleTop = baseLineY - arcRadius
        pathCircleBottom = baseLineY + arcRadius
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lineWidth = w.toFloat() - paddingLeft.toFloat() - paddingRight.toFloat()
        circleRadius = lineWidth / 2f / Math.sin(circleAngle / 2f / 360 * 2 * Math.PI).toFloat()
        circleY = baseLineY - circleRadius * Math.cos(circleAngle / 2f / 360f * 2 * Math.PI).toFloat()
        pathBorderLeft = paddingLeft.toFloat()
        pathBorderRight = paddingLeft.toFloat() + lineWidth
    }

    override fun onDraw(canvas: Canvas) {
        val percent = progress.toFloat() / max.toFloat() * 100f
        val currentAngle = Math.abs(50 - percent) * circleAngle / 100f
        val x = if (percent < 50) {
            lineWidth / 2f - circleRadius * Math.cos((90f - currentAngle) / 360f * 2 * Math.PI)
        } else {
            lineWidth / 2f + circleRadius * Math.cos((90f - currentAngle) / 360f * 2 * Math.PI)
        }.toFloat()
        val y = circleY + circleRadius * Math.cos(currentAngle / 360f * 2 * Math.PI).toFloat() + currentJump

        val drawX = pathBorderLeft + x
        totalPath.reset()
        totalPath.addCircle(pathBorderLeft, baseLineY, arcRadius, Path.Direction.CW)
        totalPath.addCircle(pathBorderRight, baseLineY, arcRadius, Path.Direction.CW)
        totalPath.moveTo(pathBorderLeft, pathCircleTop)
        totalPath.lineTo(drawX, y - arcRadius)
        totalPath.lineTo(pathBorderRight, pathCircleTop)
        totalPath.lineTo(pathBorderRight, pathCircleBottom)
        totalPath.lineTo(drawX, y + arcRadius)
        totalPath.lineTo(pathBorderLeft, pathCircleBottom)
        totalPath.close()
        canvas.drawPath(totalPath, totalPaint)

        progressPath.reset()
        progressPath.addCircle(pathBorderLeft, baseLineY, arcRadius, Path.Direction.CW)
        progressPath.moveTo(pathBorderLeft, pathCircleTop)
        progressPath.lineTo(drawX, y - arcRadius)
        progressPath.lineTo(drawX, y + arcRadius)
        progressPath.lineTo(pathBorderLeft, pathCircleBottom)
        progressPath.close()
        canvas.drawPath(progressPath, progressPaint)
        canvas.drawCircle(drawX, y, arcRadius, progressPaint)

        val id = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null, Canvas.ALL_SAVE_FLAG)
        if (percent < 50) {
            canvas.rotate(currentAngle + currentQuiverAngle, drawX, y - arcRadius)
        } else {
            canvas.rotate(-currentAngle + currentQuiverAngle, drawX, y - arcRadius)
        }
        canvas.drawBitmap(progressBitmap, drawX - percentBackgroundHalfWidth, y - arcRadius - progressBitmap.height, null)
        val textY = y - arcRadius - textBaseLine + textBaseLineOffset
        canvas.drawText("${percent.toInt()}%", drawX, textY, percentTextPaint)
        canvas.restoreToCount(id)

        if (isLarge) {
            currentQuiverAngle += changeAngle
        } else {
            currentQuiverAngle -= changeAngle
        }
        if (currentQuiverAngle > quiverAngle) {
            isLarge = false
        }
        if (currentQuiverAngle < -quiverAngle) {
            isLarge = true
        }
        currentJumpHeight = ((50 - Math.abs(50 - percent.toInt())) / 50f) * maxJumpHeight
        if (currentJumpHeight != 0f) {
            if (isTop) {
                currentJump += changeHeight
            } else {
                currentJump -= changeHeight
            }
        } else {
            currentJump = 0f
        }
        if (currentJump > currentJumpHeight) {
            isTop = false
        }
        if (currentJump < -currentJumpHeight) {
            isTop = true
        }
        invalidate()
    }

    private fun calcTextBaseline(): Float {
        val fontMetrics = percentTextPaint.getFontMetrics();
        return (fontMetrics.descent - fontMetrics.ascent) / 2 - fontMetrics.descent;
    }

    fun setTotalColor(color: Int) {
        totalPaint.color = color
    }

    fun setProgressColor(color: Int) {
        progressPaint.color = color
    }

    fun setTextSize(px: Float) {
        percentTextPaint.textSize = px
    }

    fun setTextSize(unit: Int, textSize: Float) {
        percentTextPaint.textSize = TypedValue.applyDimension(unit, textSize, context.resources.displayMetrics)
    }

    fun setTextColor(color: Int) {
        percentTextPaint.color = color
    }

    fun setBaseLineY(y: Float) {
        this.baseLineY = y
        circleY = baseLineY - circleRadius * Math.cos(circleAngle / 2f / 360f * 2 * Math.PI).toFloat()
        pathCircleTop = baseLineY - arcRadius
        pathCircleBottom = baseLineY + arcRadius
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        lineWidth = width.toFloat() - left.toFloat() - right.toFloat()
        circleRadius = lineWidth / 2f / Math.sin(circleAngle / 2f / 360 * 2 * Math.PI).toFloat()
        circleY = baseLineY - circleRadius * Math.cos(circleAngle / 2f / 360f * 2 * Math.PI).toFloat()
        pathBorderLeft = paddingLeft.toFloat()
        pathBorderRight = paddingLeft.toFloat() + lineWidth
    }

    /**
     * 不要设置太大,建议30以内,否则很容易被看出来有问题
     */
    fun setCircleAngle(angle: Float) {
        this.changeAngle = angle
        circleRadius = lineWidth / 2f / Math.sin(circleAngle / 2f / 360 * 2 * Math.PI).toFloat()
        circleY = baseLineY - circleRadius * Math.cos(circleAngle / 2f / 360f * 2 * Math.PI).toFloat()
    }

    fun setLineHeight(height: Float) {
        this.arcRadius = height / 2f
        pathCircleTop = baseLineY - arcRadius
        pathCircleBottom = baseLineY + arcRadius
    }

    fun setMarkerResource(resId: Int) {
        setMarkerResource(resId, 0)
    }

    fun setMarkerResource(resId: Int, color: Int) {
        val drawable = ContextCompat.getDrawable(context, resId)
        drawable?.also {
            if (color != 0) {
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.draw(canvas)
            val textWidth = percentTextPaint.measureText("100%") + percentTextPaddingHorizontal * 2f
            val matrix = Matrix()
            val scale = textWidth / drawable.intrinsicWidth.toFloat()
            matrix.postScale(scale, scale)
            progressBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            percentBackgroundHalfWidth = progressBitmap.width.toFloat() / 2f
        }
    }
}