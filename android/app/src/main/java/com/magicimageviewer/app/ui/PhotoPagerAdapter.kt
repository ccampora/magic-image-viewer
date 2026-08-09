package com.magicimageviewer.app.ui

import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.magicimageviewer.app.databinding.ItemPhotoBinding
import kotlin.math.abs

/**
 * Shows one photo per page. ViewPager2 is set to VERTICAL orientation so
 * up/down swipes page through photos; a horizontal fling on the image is
 * intercepted here and reported as a "transfer to PC" gesture instead of
 * being treated as page navigation.
 */
class PhotoPagerAdapter(
    private val photos: List<Uri>,
    private val onSwipeRightToTransfer: (Uri) -> Unit
) : RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = photos[position]
        val imageView: ImageView = holder.binding.photoImage
        Glide.with(imageView).load(uri).into(imageView)

        val gestureDetector = GestureDetector(
            imageView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                // Must return true, or Android's touch dispatch never delivers the
                // follow-up MOVE/UP events to this listener, so onFling never fires.
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    val isHorizontal = abs(dx) > abs(dy)
                    val isRightSwipe = dx > SWIPE_DISTANCE_THRESHOLD
                    val isFastEnough = abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    if (isHorizontal && isRightSwipe && isFastEnough) {
                        onSwipeRightToTransfer(uri)
                        return true
                    }
                    return false
                }
            }
        )
        imageView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    override fun getItemCount(): Int = photos.size

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 120
        private const val SWIPE_VELOCITY_THRESHOLD = 200
    }
}
