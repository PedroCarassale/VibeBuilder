package com.vibebuilder.app.ui.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeEncoder {
    fun encode(text: String, sizePx: Int): Bitmap? {
        if (text.isBlank() || sizePx <= 0) return null

        return runCatching {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }.getOrNull()
    }
}
