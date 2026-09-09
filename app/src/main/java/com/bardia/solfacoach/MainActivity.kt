package com.bardia.solfacoach

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity() {

    data class Note(val solfege: String, val western: String, val hz: Double)
    data class Song(val name: String, val notes: List<String>)
    data class Plan(val title: String, val price: Int, val months: Int)

    companion object {
        // TRUE = pressing a payment plan simulates successful payment for testing.
        // Before publishing, set FALSE and connect startRealPayment() to your payment backend.
        const val DEMO_PAYMENT = true
    }

    private val notes = linkedMapOf(
        "Do" to Note("Do", "C4", 261.6256),
        "Re" to Note("Re", "D4", 293.6648),
        "Mi" to Note("Mi", "E4", 329.6276),
        "Fa" to Note("Fa", "F4", 349.2282),
        "Sol" to Note("Sol", "G4", 391.9954),
        "La" to Note("La", "A4", 440.0),
        "Ti" to Note("Ti", "B4", 493.8833),
        "Do↑" to Note("Do↑", "C5", 523.2511)
    )

    private val patterns = listOf(
        listOf("Do","Re","Mi","Re","Do"),
        listOf("Do","Mi","Sol","Mi","Re","Fa"),
        listOf("Do","Re","Mi","Fa","Sol","La","Ti","Do↑","Ti","La","Sol")
    )

    private val songs = listOf(
        Song("Ode to Joy", listOf("Mi","Mi","Fa","Sol","Sol","Fa","Mi","Re","Do","Do","Re","Mi")),
        Song("Twinkle Twinkle", listOf("Do","Do","Sol","Sol","La","La","Sol","Fa","Fa","Mi","Mi","Re","Re","Do")),
        Song("Happy Birthday", listOf("Sol","Sol","La","Sol","Do↑","Ti","Sol","Sol","La","Sol","Re","Do")),
        Song("Amazing Grace", listOf("Sol","Do","Mi","Do","Mi","Re","Do","La","Sol"))
    )

    private val plans = listOf(
        Plan("یک‌ماهه", 199_000, 1),
        Plan("سه‌ماهه", 499_000, 3),
        Plan("شش‌ماهه", 799_000, 6)
    )

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var statusTop: TextView
    private lateinit var premiumBadge: TextView
    private var currentMode = 0
    private var selectedNote = "Do"
    private var selectedPattern = 0
    private var selectedSong = 0
    private var sequenceIndex = 0

    private val listening = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private val ui = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null
    private var hzText: TextView? = null
    private var centsText: TextView? = null
    private var detectedText: TextView? = null
    private var progressText: TextView? = null
    private var micButton: Button? = null
    private var stableFrames = 0

    private val prefs by lazy { getSharedPreferences("premium", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePaymentCallback(intent)
        buildUi()
        requestMicIfNeeded()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handlePaymentCallback(intent)
            if (::premiumBadge.isInitialized) refreshPremiumUi()
        }
    }

    private fun requestMicIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else statusTop.text = "🎙 میکروفون آماده است"
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            statusTop.text = "🎙 میکروفون آماده است"
        else statusTop.text = "⚠ برای تمرین صوتی اجازه میکروفون لازم است"
    }

    private fun isPremium(): Boolean = prefs.getLong("premium_until", 0L) > System.currentTimeMillis()

    private fun premiumUntil(): Long = prefs.getLong("premium_until", 0L)

    private fun activatePremium(months: Int) {
        val cal = Calendar.getInstance()
        val existing = premiumUntil()
        if (existing > System.currentTimeMillis()) cal.timeInMillis = existing
        cal.add(Calendar.MONTH, months)
        prefs.edit().putLong("premium_until", cal.timeInMillis).apply()
        refreshPremiumUi()
        buildMode()
        toast("پریمیوم تا ${formatDate(cal.timeInMillis)} فعال شد ✓")
    }

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ms))

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(30))
            setBackgroundColor(Color.rgb(7,17,38))
        }
        scroll.addView(root)
        setContentView(scroll)

        // Developer logo at the top
        val logo = ImageView(this).apply {
            setImageResource(com.bardia.solfacoach.R.drawable.developer_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = "Bardia Arman AI Developer Logo"
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(116)))

        val dev = text("توسعه‌دهنده: بردیا آرمان • توسعه هوش مصنوعی سازمانی", 12, Color.rgb(160,181,221), false)
        dev.gravity = Gravity.CENTER
        dev.setPadding(0,0,0,dp(8))
        root.addView(dev)

        val title = text("🎵  مربی سولفژ", 28, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        root.addView(title)

        val subtitle = text("تمرین سلفژ، آواز و تشخیص Pitch", 14, Color.rgb(165,184,226), false)
        subtitle.gravity = Gravity.CENTER
        root.addView(subtitle)

        premiumBadge = text("", 13, Color.WHITE, true)
        premiumBadge.gravity = Gravity.CENTER
        premiumBadge.setPadding(dp(10),dp(9),dp(10),dp(9))
        premiumBadge.setOnClickListener { showPremiumDialog() }
        root.addView(premiumBadge, LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,dp(10),0,dp(4))})
        refreshPremiumUi()

        statusTop = text("🎙 در انتظار مجوز میکروفون...", 13, Color.rgb(46,221,160), true)
        statusTop.gravity = Gravity.CENTER
        statusTop.setPadding(0, dp(8),0,dp(5))
        root.addView(statusTop)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val tabNames = listOf("نت تکی","ترکیب نت‌ها","آهنگ‌ها")
        tabNames.forEachIndexed { i, name ->
            val b = button(name, if(i==0) Color.rgb(78,113,255) else Color.rgb(25,43,78))
            b.setOnClickListener {
                currentMode = i
                stopListening()
                buildMode()
            }
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(3),dp(8),dp(3),dp(8))
            })
        }
        root.addView(tabs)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        buildMode()
    }

    private fun refreshPremiumUi() {
        if (!::premiumBadge.isInitialized) return
        if (isPremium()) {
            premiumBadge.text = "⭐ نسخه پریمیوم فعال • تا ${formatDate(premiumUntil())}"
            premiumBadge.background = rounded(Color.rgb(18,139,94), Color.rgb(42,205,147), 14f)
        } else {
            premiumBadge.text = "🔒 نسخه رایگان • ارتقا به پریمیوم"
            premiumBadge.background = rounded(Color.rgb(69,45,112), Color.rgb(147,84,225), 14f)
        }
    }

    private fun buildMode() {
        content.removeAllViews()
        statusText=null; hzText=null; centsText=null; detectedText=null; progressText=null; micButton=null
        when(currentMode) {
            0 -> buildSingle()
            1 -> buildPattern()
            else -> buildSongs()
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16),dp(16),dp(16),dp(16))
        background = rounded(Color.rgb(15,28,55), Color.rgb(42,71,122), 22f)
    }

    private fun buildSingle() {
        val c=card()
        c.addView(text("🎵 تمرین نت تکی",23,Color.WHITE,true))
        c.addView(text("نسخه رایگان: Do، Re و Mi باز هستند.",13,Color.rgb(160,181,221),false))

        val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val keys=notes.keys.toList()
        for(r in 0..1){
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            for(j in 0..3){
                val idx=r*4+j
                val key=keys[idx]
                val unlocked=isPremium() || idx<3
                val label=if(unlocked) key else "🔒 $key"
                val b=button(label, if(key==selectedNote && unlocked) Color.rgb(78,113,255) else Color.rgb(24,43,78))
                b.setOnClickListener{
                    if(!unlocked){ showPremiumDialog(); return@setOnClickListener }
                    selectedNote=key; buildMode()
                }
                row.addView(b,LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(dp(3),dp(5),dp(3),dp(5))})
            }
            grid.addView(row)
        }
        c.addView(grid)

        val target=notes[selectedNote]!!
        val big=text(selectedNote,54,Color.WHITE,true).apply{gravity=Gravity.CENTER}
        c.addView(big)
        val f=text("${target.western} • ${"%.2f".format(target.hz)} Hz",16,Color.rgb(166,188,236),false).apply{gravity=Gravity.CENTER}
        c.addView(f)
        val play=button("🔊 پخش نت",Color.rgb(56,91,190))
        play.setOnClickListener{playTone(target.hz,850)}
        c.addView(play,LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(0,dp(14),0,dp(8))})
        addMicArea(c,"شروع خواندن")
        content.addView(c,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(8),0,0)})
    }

    private fun buildPattern() {
        val c=card()
        c.addView(text("🔗 ترکیب نت‌ها",23,Color.WHITE,true))
        c.addView(text("در نسخه رایگان فقط الگوی اول باز است.",13,Color.rgb(160,181,221),false))
        val levels=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("آسان","متوسط","پیشرفته").forEachIndexed{i,n->
            val unlocked=isPremium() || i==0
            val b=button(if(unlocked)n else "🔒 $n", if(i==selectedPattern && unlocked) Color.rgb(30,177,126) else Color.rgb(21,73,61))
            b.setOnClickListener{
                if(!unlocked){showPremiumDialog();return@setOnClickListener}
                selectedPattern=i;sequenceIndex=0;buildMode()
            }
            levels.addView(b,LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(dp(3),dp(7),dp(3),dp(7))})
        }
        c.addView(levels)
        val pattern=patterns[selectedPattern]
        c.addView(text(pattern.joinToString(" – "),22,Color.WHITE,true).apply{
            gravity=Gravity.CENTER;setPadding(0,dp(14),0,dp(14))
        })
        val play=button("▶ پخش الگو",Color.rgb(20,147,104))
        play.setOnClickListener{playSequence(pattern)}
        c.addView(play,LinearLayout.LayoutParams(-1,dp(50)))
        progressText=text("نت هدف: ${pattern[0]}   •   1 / ${pattern.size}",15,Color.rgb(70,231,173),true).apply{
            gravity=Gravity.CENTER;setPadding(0,dp(14),0,0)
        }
        c.addView(progressText)
        addMicArea(c,"شروع خواندن الگو")
        content.addView(c,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(8),0,0)})
    }

    private fun buildSongs() {
        val c=card()
        c.addView(text("🎼 آهنگ‌ها",23,Color.WHITE,true))
        c.addView(text("در نسخه رایگان فقط آهنگ اول باز است.",13,Color.rgb(160,181,221),false))

        songs.forEachIndexed{i,s->
            val unlocked=isPremium() || i==0
            val emoji=when(i){0->"🌄";1->"⭐";2->"🎂";else->"🌅"}
            val b=button("${if(!unlocked)"🔒 " else ""}$emoji  ${s.name}",if(i==selectedSong && unlocked)Color.rgb(108,53,166) else Color.rgb(42,28,79))
            b.setOnClickListener{
                if(!unlocked){showPremiumDialog();return@setOnClickListener}
                selectedSong=i;sequenceIndex=0;buildMode()
            }
            c.addView(b,LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(5),0,0)})
        }

        val song=songs[selectedSong]
        c.addView(text(song.notes.joinToString("  "),16,Color.WHITE,true).apply{
            gravity=Gravity.CENTER;setPadding(0,dp(15),0,dp(10))
        })
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val play=button("▶ پخش ملودی",Color.rgb(85,58,185)).apply{setOnClickListener{playSequence(song.notes)}}
        val step=button("🎵 نت هدف",Color.rgb(65,52,112)).apply{
            setOnClickListener{
                val n=notes[song.notes[sequenceIndex.coerceAtMost(song.notes.lastIndex)]]!!
                playTone(n.hz,700)
            }
        }
        row.addView(play,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(3),0,dp(3),0)})
        row.addView(step,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(3),0,dp(3),0)})
        c.addView(row)
        progressText=text("نت هدف: ${song.notes[0]}   •   1 / ${song.notes.size}",15,Color.rgb(205,123,255),true).apply{
            gravity=Gravity.CENTER;setPadding(0,dp(14),0,0)
        }
        c.addView(progressText)
        addMicArea(c,"شروع خواندن آهنگ")
        content.addView(c,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(8),0,0)})
    }

    private fun addMicArea(c:LinearLayout,label:String){
        micButton=button("🎙  $label",Color.rgb(203,58,113)).apply{
            textSize=17f
            setOnClickListener{if(listening.get())stopListening() else startListening()}
        }
        c.addView(micButton,LinearLayout.LayoutParams(-1,dp(58)).apply{setMargins(0,dp(16),0,dp(8))})
        statusText=text("منتظر شروع...",14,Color.rgb(193,205,232),true).apply{
            gravity=Gravity.CENTER;setPadding(dp(10),dp(12),dp(10),dp(12))
            background=rounded(Color.rgb(9,21,43),Color.rgb(37,61,101),14f)
        }
        c.addView(statusText)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        hzText=metric("– Hz","فرکانس شما")
        centsText=metric("–","اختلاف Cent")
        detectedText=metric("–","نت تشخیص داده")
        row.addView(hzText,LinearLayout.LayoutParams(0,dp(80),1f).apply{setMargins(dp(3),dp(8),dp(3),0)})
        row.addView(centsText,LinearLayout.LayoutParams(0,dp(80),1f).apply{setMargins(dp(3),dp(8),dp(3),0)})
        row.addView(detectedText,LinearLayout.LayoutParams(0,dp(80),1f).apply{setMargins(dp(3),dp(8),dp(3),0)})
        c.addView(row)
    }

    private fun showPremiumDialog() {
        val wrap=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(10),dp(18),dp(8))
        }
        wrap.addView(text("همه نت‌ها، الگوها و آهنگ‌ها را باز کن.",14,Color.DKGRAY,false))
        plans.forEach{plan->
            val formatted=NumberFormat.getNumberInstance(Locale.US).format(plan.price)
            val b=Button(this).apply{
                text="${plan.title}  •  $formatted تومان"
                isAllCaps=false
                textSize=16f
                setOnClickListener{
                    if(DEMO_PAYMENT){
                        activatePremium(plan.months)
                        (parent?.parent as? View)?.let{}
                        toast("پرداخت آزمایشی موفق بود")
                    }else{
                        startRealPayment(plan)
                    }
                }
            }
            wrap.addView(b,LinearLayout.LayoutParams(-1,dp(55)).apply{setMargins(0,dp(7),0,0)})
        }
        val note = text(
            if(DEMO_PAYMENT) "حالت تست فعال است: انتخاب هر پلن مثل پرداخت موفق عمل می‌کند." 
            else "پس از پرداخت و تأیید سرور، پریمیوم خودکار فعال می‌شود.",
            12, Color.GRAY, false
        )
        note.setPadding(0,dp(8),0,0)
        wrap.addView(note)

        AlertDialog.Builder(this)
            .setTitle("⭐ پریمیوم مربی سولفژ")
            .setView(wrap)
            .setNegativeButton("بعداً",null)
            .show()
    }

    private fun startRealPayment(plan: Plan) {
        // PRODUCTION INTEGRATION POINT:
        // 1) Send plan + user/device account to YOUR backend over HTTPS.
        // 2) Backend creates a payment request with the chosen Iranian gateway.
        // 3) Open returned payment URL here.
        // 4) Gateway redirects to solfacoach://payment?status=ok&token=...
        // 5) App sends token to backend. ONLY backend verifies payment.
        // 6) Backend returns expiry date; then save premium_until.
        AlertDialog.Builder(this)
            .setTitle("اتصال درگاه")
            .setMessage("برای پرداخت واقعی باید Merchant ID و آدرس Backend درگاه اضافه شود. منطق قفل و تاریخ اشتراک آماده است.")
            .setPositiveButton("باشه",null)
            .show()
    }

    private fun handlePaymentCallback(intent:Intent?) {
        val data:Uri=intent?.data ?: return
        if(data.scheme=="solfacoach" && data.host=="payment"){
            // Do NOT trust status from deep link in production.
            // Verify the token with backend first, then activate.
        }
    }

    private fun startListening(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),100);return
        }
        val rate=44100
        val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        val size=max(min,4096)
        audioRecord=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size*2)
        if(audioRecord?.state!=AudioRecord.STATE_INITIALIZED){statusText?.text="خطا در راه‌اندازی میکروفون";return}
        sequenceIndex=0;stableFrames=0
        audioRecord?.startRecording();listening.set(true)
        micButton?.text="■ توقف"
        statusText?.text="در حال گوش دادن... صدای «آ» را واضح نگه دار"

        thread(start=true,name="pitch-reader"){
            val buf=ShortArray(4096)
            while(listening.get()){
                val n=audioRecord?.read(buf,0,buf.size)?:0
                if(n>1500){
                    val freq=detectPitch(buf,n,rate)
                    if(freq in 80.0..1000.0){
                        val nearest=nearestNote(freq)
                        val targetName=when(currentMode){
                            0->selectedNote
                            1->patterns[selectedPattern][sequenceIndex.coerceAtMost(patterns[selectedPattern].lastIndex)]
                            else->songs[selectedSong].notes[sequenceIndex.coerceAtMost(songs[selectedSong].notes.lastIndex)]
                        }
                        val cents=1200.0*log2(freq/notes[targetName]!!.hz)
                        ui.post{updateDetection(freq,nearest,cents,targetName)}
                    }
                }
            }
        }
    }

    private fun stopListening(){
        listening.set(false)
        try{audioRecord?.stop()}catch(_:Exception){}
        try{audioRecord?.release()}catch(_:Exception){}
        audioRecord=null
        micButton?.text=when(currentMode){0->"🎙  شروع خواندن";1->"🎙  شروع خواندن الگو";else->"🎙  شروع خواندن آهنگ"}
    }

    private fun updateDetection(freq:Double,nearest:Pair<String,Double>,cents:Double,targetName:String){
        hzText?.text="${"%.1f".format(freq)} Hz\nفرکانس شما"
        centsText?.text="${cents.roundToInt()}\nاختلاف Cent"
        detectedText?.text="${nearest.first}\nنت تشخیص داده"
        if(abs(cents)<=35){
            stableFrames++
            statusText?.setTextColor(Color.rgb(55,230,167))
            statusText?.text="✓ $targetName درست است — نگه دار..."
            if(stableFrames>=4){
                stableFrames=0
                if(currentMode==0){
                    statusText?.text="🎉 عالی! $targetName صحیح بود."
                    stopListening()
                }else{
                    val seq=if(currentMode==1)patterns[selectedPattern] else songs[selectedSong].notes
                    sequenceIndex++
                    if(sequenceIndex>=seq.size){
                        statusText?.text="🎉 تمرین کامل شد!"
                        stopListening()
                    }else{
                        progressText?.text="نت هدف: ${seq[sequenceIndex]}   •   ${sequenceIndex+1} / ${seq.size}"
                        statusText?.text="✓ درست بود. حالا ${seq[sequenceIndex]} را بخوان"
                    }
                }
            }
        }else{
            stableFrames=0
            statusText?.setTextColor(Color.rgb(255,205,92))
            statusText?.text=if(cents<0)"⬆ کمی بالاتر بخوان برای $targetName" else "⬇ کمی پایین‌تر بخوان برای $targetName"
        }
    }

    private fun nearestNote(freq:Double):Pair<String,Double>{
        var best="";var bestC=Double.MAX_VALUE
        notes.forEach{(name,n)->
            val c=abs(1200.0*log2(freq/n.hz))
            if(c<bestC){bestC=c;best=name}
        }
        return best to bestC
    }

    private fun detectPitch(data:ShortArray,n:Int,sampleRate:Int):Double{
        var rms=0.0
        for(i in 0 until n){val v=data[i]/32768.0;rms+=v*v}
        rms=sqrt(rms/n);if(rms<0.015)return -1.0
        val minLag=(sampleRate/1000.0).toInt().coerceAtLeast(20)
        val maxLag=(sampleRate/80.0).toInt().coerceAtMost(n/2)
        var bestLag=-1;var bestCorr=Double.NEGATIVE_INFINITY
        for(lag in minLag..maxLag){
            var sum=0.0;var sumA=0.0;var sumB=0.0
            val limit=n-lag
            var i=0
            while(i<limit){
                val a=data[i].toDouble();val b=data[i+lag].toDouble()
                sum+=a*b;sumA+=a*a;sumB+=b*b;i+=2
            }
            val corr=sum/(sqrt(sumA*sumB)+1e-9)
            if(corr>bestCorr){bestCorr=corr;bestLag=lag}
        }
        return if(bestLag<=0||bestCorr<0.45)-1.0 else sampleRate.toDouble()/bestLag
    }

    private fun playTone(freq:Double,durationMs:Int){
        thread{playToneBlocking(freq,durationMs)}
    }

    private fun playSequence(seq:List<String>){
        thread{
            seq.forEach{
                playToneBlocking(notes[it]!!.hz,520)
                Thread.sleep(120)
            }
        }
    }

    private fun playToneBlocking(freq:Double,durationMs:Int){
        val sr=44100
        val count=(sr*durationMs/1000.0).toInt()
        val samples=ShortArray(count)
        for(i in samples.indices){
            val env=min(1.0,min(i/700.0,(count-i)/900.0))
            samples[i]=(sin(2.0*Math.PI*i*freq/sr)*0.22*Short.MAX_VALUE*env).toInt().toShort()
        }
        val track=AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,samples.size*2,AudioTrack.MODE_STATIC)
        track.write(samples,0,samples.size);track.play()
        Thread.sleep(durationMs.toLong()+30);track.release()
    }

    private fun metric(top:String,bottom:String)=text("$top\n$bottom",13,Color.WHITE,true).apply{
        gravity=Gravity.CENTER;background=rounded(Color.rgb(10,23,46),Color.rgb(37,61,101),12f)
    }

    private fun button(label:String,color:Int)=Button(this).apply{
        text=label;setTextColor(Color.WHITE);textSize=14f;isAllCaps=false;gravity=Gravity.CENTER
        background=rounded(color,Color.TRANSPARENT,14f);setPadding(dp(8),0,dp(8),0)
    }

    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply{
        text=s;textSize=size.toFloat();setTextColor(color)
        if(bold)setTypeface(typeface,Typeface.BOLD)
    }

    private fun rounded(fill:Int,stroke:Int,radius:Float)=GradientDrawable().apply{
        shape=GradientDrawable.RECTANGLE;setColor(fill);cornerRadius=dp(radius.toInt()).toFloat()
        if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
