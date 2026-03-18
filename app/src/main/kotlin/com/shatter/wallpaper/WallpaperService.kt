package com.shatter.wallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.*

data class Shard(
    val path: Path,
    val cx: Float, val cy: Float,
    val bright: Float,
    val tiltAx: Float, val tiltAy: Float,
    val litEdges: List<Float>,
    val edgePts: List<FloatArray>
)

class ShatterWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ShatterEngine()

    inner class ShatterEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var running = false
        private var w = 1080
        private var h = 1920
        private val shards = mutableListOf<Shard>()
        private var shardsBuilt = false
        private val startTime = System.currentTimeMillis()

        private var rngS = 0xC0FFEE42L
        private fun rng(): Float {
            rngS = rngS xor (rngS shl 13)
            rngS = rngS xor (rngS ushr 17)
            rngS = rngS xor (rngS shl 5)
            return (rngS and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL.toFloat()
        }
        private fun rr(a: Float, b: Float) = a + rng() * (b - a)

        private fun buildShards(sw: Int, sh: Int) {
            rngS = 0xC0FFEE42L
            val hx = sw * 0.74f
            val hy = sh * 0.11f
            val clkR = sw * 0.126f

            val seeds = mutableListOf<FloatArray>()
            for (i in 0..9) {
                val a = rng() * PI.toFloat() * 2f
                val r = rr(clkR * 0.9f, clkR * 3.0f)
                seeds.add(floatArrayOf(hx + cos(a) * r, hy + sin(a) * r))
            }
            for (i in 0..9) seeds.add(floatArrayOf(rr(50f, sw-50f), rr(sh*0.12f, sh-50f)))
            val bd = 200f
            seeds.addAll(listOf(
                floatArrayOf(-bd,sh*.1f),floatArrayOf(-bd,sh*.5f),floatArrayOf(-bd,sh*.85f),
                floatArrayOf(sw+bd,sh*.2f),floatArrayOf(sw+bd,sh*.6f),floatArrayOf(sw+bd,sh*.9f),
                floatArrayOf(sw*.2f,-bd),floatArrayOf(sw*.65f,-bd),
                floatArrayOf(sw*.15f,sh+bd),floatArrayOf(sw*.55f,sh+bd),floatArrayOf(sw*.85f,sh+bd)
            ))
            val NS = seeds.size
            val PAD = 300f

            fun clip(poly: List<FloatArray>, si: FloatArray, sj: FloatArray): List<FloatArray> {
                if (poly.isEmpty()) return emptyList()
                val mx=(si[0]+sj[0])/2f; val my=(si[1]+sj[1])/2f
                val nx=sj[0]-si[0]; val ny=sj[1]-si[1]
                val out = mutableListOf<FloatArray>()
                for (k in poly.indices) {
                    val a=poly[k]; val b=poly[(k+1)%poly.size]
                    val da=(a[0]-mx)*nx+(a[1]-my)*ny
                    val db=(b[0]-mx)*nx+(b[1]-my)*ny
                    if (da<=0f) out.add(a)
                    if ((da<=0f)!=(db<=0f)) {
                        val t=da/(da-db)
                        out.add(floatArrayOf(a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])))
                    }
                }
                return out
            }

            val LDX=-0.62f; val LDY=-0.78f
            shards.clear()

            for (i in 0 until NS) {
                val si = seeds[i]
                var poly: List<FloatArray> = listOf(
                    floatArrayOf(-PAD,-PAD),floatArrayOf(sw+PAD,-PAD),
                    floatArrayOf(sw+PAD,sh+PAD),floatArrayOf(-PAD,sh+PAD)
                )
                for (j in 0 until NS) {
                    if (j==i) continue
                    poly = clip(poly,si,seeds[j])
                    if (poly.isEmpty()) break
                }
                if (poly.size<3) continue

                var cx2=0f; var cy2=0f
                poly.forEach { p -> cx2+=p[0]; cy2+=p[1] }
                cx2/=poly.size; cy2/=poly.size

                val dx=si[0]-hx; val dy=si[1]-hy
                val dist=sqrt(dx*dx+dy*dy)+1f
                val force=min(sw*0.46f/dist,1f)*rr(0.7f,1.3f)
                val dispX=(dx/dist)*force*rr(sw*0.026f,sw*0.089f)
                val dispY=(dy/dist)*force*rr(sh*0.015f,sh*0.050f)
                val rot=(rng()-0.5f)*force*0.16f
                val bright=rr(0.50f,1.55f)
                val tiltAx=rr(-0.7f,0.7f)
                val tiltAy=rr(-0.7f,0.7f)

                val matrix=Matrix()
                matrix.setTranslate(cx2+dispX,cy2+dispY)
                matrix.preRotate(rot*180f/PI.toFloat(),0f,0f)
                matrix.preTranslate(-cx2,-cy2)

                val pts=FloatArray(poly.size*2)
                for (k in poly.indices) { pts[k*2]=poly[k][0]; pts[k*2+1]=poly[k][1] }
                matrix.mapPoints(pts)

                val path=Path()
                path.moveTo(pts[0],pts[1])
                for (k in 1 until poly.size) path.lineTo(pts[k*2],pts[k*2+1])
                path.close()

                val ndots=mutableListOf<Float>()
                val edges=mutableListOf<FloatArray>()
                for (k in poly.indices) {
                    val p1=poly[k]; val p2=poly[(k+1)%poly.size]
                    val ex=p2[0]-p1[0]; val ey=p2[1]-p1[1]
                    val el=sqrt(ex*ex+ey*ey)+1e-5f
                    ndots.add(ey/el*LDX+(-ex/el)*LDY)
                    val ep=floatArrayOf(p1[0],p1[1],p2[0],p2[1])
                    matrix.mapPoints(ep,0,ep,0,2)
                    edges.add(ep)
                }

                shards.add(Shard(path,cx2+dispX,cy2+dispY,bright,tiltAx,tiltAy,ndots,edges))
            }
            shardsBuilt=true
        }

        private val edgePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
        private val shardPaint=Paint(Paint.ANTI_ALIAS_FLAG)

        private fun drawFrame(canvas: Canvas) {
            val t=(System.currentTimeMillis()-startTime)/1000f
            val shimmer=0.5f+0.5f*sin(t*0.20f)
            canvas.drawColor(Color.argb(255,1,5,8))
            if (!shardsBuilt) return

            for (shard in shards) {
                canvas.save()
                canvas.clipPath(shard.path)
                edgePaint.color=Color.argb(242,1,3,10)
                edgePaint.strokeWidth=w*0.037f
                canvas.drawPath(shard.path,edgePaint)
                for (k in shard.litEdges.indices) {
                    val ndot=shard.litEdges[k]
                    val ep=shard.edgePts[k]
                    if (ndot>0.04f) {
                        val alpha=min(0.88f,ndot*0.82f+shimmer*0.08f)*shard.bright*0.72f
                        edgePaint.color=Color.argb((alpha*255).toInt().coerceIn(0,255),118,182,252)
                        edgePaint.strokeWidth=w*0.026f
                        canvas.drawLine(ep[0],ep[1],ep[2],ep[3],edgePaint)
                        val a2=min(0.65f,ndot*0.55f+shimmer*0.06f)
                        edgePaint.color=Color.argb((a2*255).toInt().coerceIn(0,255),205,238,255)
                        edgePaint.strokeWidth=w*0.002f
                        canvas.drawLine(ep[0],ep[1],ep[2],ep[3],edgePaint)
                    }
                }
                val b=shard.bright
                shardPaint.shader=RadialGradient(
                    shard.cx-28f*shard.tiltAx,shard.cy-28f*shard.tiltAy,w*0.37f,
                    intArrayOf(
                        Color.argb(224,(42*b).toInt().coerceIn(0,255),(88*b).toInt().coerceIn(0,255),(175*b).toInt().coerceIn(0,255)),
                        Color.argb(235,(20*b).toInt().coerceIn(0,255),(52*b).toInt().coerceIn(0,255),(118*b).toInt().coerceIn(0,255)),
                        Color.argb(242,(8*b).toInt().coerceIn(0,255),(24*b).toInt().coerceIn(0,255),(62*b).toInt().coerceIn(0,255))
                    ),
                    floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP
                )
                canvas.drawPath(shard.path,shardPaint)
                shardPaint.shader=null
                canvas.restore()
            }

            val hx=w*0.74f; val hy=h*0.11f
            val gp=Paint(Paint.ANTI_ALIAS_FLAG)
            gp.shader=RadialGradient(hx,hy,w*0.44f,
                intArrayOf(Color.argb(((0.26f+shimmer*0.08f)*255).toInt().coerceIn(0,255),12,40,115),Color.argb(26,5,18,58),Color.TRANSPARENT),
                floatArrayOf(0f,0.4f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),gp)

            drawClock(canvas,shimmer)

            val vp=Paint(Paint.ANTI_ALIAS_FLAG)
            vp.shader=RadialGradient(w*0.5f,h*0.5f,h*0.9f,
                intArrayOf(Color.TRANSPARENT,Color.argb(38,0,2,8),Color.argb(191,0,1,6)),
                floatArrayOf(0f,0.6f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),vp)
        }

        private fun drawClock(canvas: Canvas, shimmer: Float) {
            val cx=w*0.74f; val cy=h*0.11f; val r=w*0.126f
            val fp=Paint(Paint.ANTI_ALIAS_FLAG)
            fp.shader=RadialGradient(cx-r*.20f,cy-r*.22f,r,
                intArrayOf(Color.argb(209,16,44,98),Color.argb(224,9,26,65),Color.argb(245,3,9,28)),
                floatArrayOf(0f,0.55f,1f),Shader.TileMode.CLAMP)
            canvas.drawCircle(cx,cy,r,fp)

            val rp=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style=Paint.Style.STROKE; strokeWidth=r*0.030f
                color=Color.argb(((0.55f+shimmer*0.12f)*255).toInt(),88,155,235)
            }
            canvas.drawCircle(cx,cy,r,rp)

            val tp=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
            for (i in 0..59) {
                val a=i/60f*PI.toFloat()*2f-PI.toFloat()/2f
                val big=i%5==0
                tp.strokeWidth=if(big) r*0.030f else r*0.012f
                tp.color=if(big) Color.argb(189,118,182,250) else Color.argb(66,58,112,195)
                val r1=r-r*0.06f; val r2=r1-(if(big) r*0.16f else r*0.07f)
                canvas.drawLine(cx+cos(a)*r1,cy+sin(a)*r1,cx+cos(a)*r2,cy+sin(a)*r2,tp)
            }

            val cal=java.util.Calendar.getInstance()
            val sec=cal.get(java.util.Calendar.SECOND)+cal.get(java.util.Calendar.MILLISECOND)/1000f
            val min=cal.get(java.util.Calendar.MINUTE)+sec/60f
            val hour=(cal.get(java.util.Calendar.HOUR_OF_DAY)%12)+min/60f
            val hp=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }

            val ha=hour/12f*PI.toFloat()*2f-PI.toFloat()/2f
            hp.strokeWidth=r*0.065f; hp.color=Color.argb(234,208,232,255)
            canvas.drawLine(cx-cos(ha)*r*.14f,cy-sin(ha)*r*.14f,cx+cos(ha)*r*.52f,cy+sin(ha)*r*.52f,hp)

            val ma=min/60f*PI.toFloat()*2f-PI.toFloat()/2f
            hp.strokeWidth=r*0.038f; hp.color=Color.argb(214,175,218,255)
            canvas.drawLine(cx-cos(ma)*r*.14f,cy-sin(ma)*r*.14f,cx+cos(ma)*r*.73f,cy+sin(ma)*r*.73f,hp)

            val sa=sec/60f*PI.toFloat()*2f-PI.toFloat()/2f
            hp.strokeWidth=r*0.018f; hp.color=Color.argb(224,45,158,255)
            canvas.drawLine(cx-cos(sa)*r*.20f,cy-sin(sa)*r*.20f,cx+cos(sa)*r*.80f,cy+sin(sa)*r*.80f,hp)

            canvas.drawCircle(cx,cy,r*0.052f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(242,178,224,255) })
        }

        private val drawRunnable=object:Runnable {
            override fun run() {
                if (!running) return
                val canvas=try { surfaceHolder.lockCanvas() } catch(e:Exception) { null }
                if (canvas!=null) {
                    try { drawFrame(canvas) } finally { surfaceHolder.unlockCanvasAndPost(canvas) }
                }
                handler.postDelayed(this,33)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder,format,width,height)
            w=width; h=height
            if (!shardsBuilt) buildShards(w,h)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            running=visible
            if (visible) handler.post(drawRunnable)
            else handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            running=false
            handler.removeCallbacks(drawRunnable)
        }
    }
}
