package com.example.minlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var pinnedBox: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText
    private lateinit var adapter: ArrayAdapter<String>

    private var apps = listOf<Pair<String, String>>()   // label to package
    private var shown = listOf<Pair<String, String>>()

    private val gd by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (drawer.visibility != View.VISIBLE && vy < -800) { openDrawer(); return true }
                return false
            }
        })
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", MODE_PRIVATE)

        val clock = TextClock(this).apply {
            format24Hour = "HH:mm  ·  EEE d/M"
            format12Hour = "HH:mm  ·  EEE d/M"
            textSize = 30f
            setTextColor(Color.WHITE)
            // nhấn giữ đồng hồ = hiện lại app đã ẩn
            setOnLongClickListener { prefs.edit().remove("hidden").apply(); refresh(); true }
        }
        pinnedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(clock)
            addView(pinnedBox)
        }

        search = EditText(this).apply {
            hint = "Tìm app"
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            // Enter = mở app đầu tiên trong kết quả
            setOnEditorActionListener { _, _, _ -> shown.firstOrNull()?.let { launch(it.second) }; true }
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, p, _ -> launch(shown[p].second) }
            setOnItemLongClickListener { _, _, p, _ -> menu(shown[p]); true }
        }
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            setPadding(16, 16, 16, 0)
            addView(search)
            addView(list)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(home)
            addView(drawer)
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); closeDrawer() }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // gõ ký tự bất kỳ trên màn hình chính -> mở tìm kiếm
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (drawer.visibility != View.VISIBLE && e.unicodeChar > 32) {
            openDrawer(e.unicodeChar.toChar().toString()); return true
        }
        return super.onKeyDown(k, e)
    }

    @Deprecated("launcher: back chỉ đóng drawer")
    override fun onBackPressed() { if (drawer.visibility == View.VISIBLE) closeDrawer() }

    private fun openDrawer(text: String = "") {
        drawer.visibility = View.VISIBLE
        search.setText(text)
        search.setSelection(text.length)
        search.requestFocus()
    }

    private fun closeDrawer() { search.setText(""); drawer.visibility = View.GONE }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        closeDrawer()
    }

    private fun refresh() {
        val hidden = prefs.getStringSet("hidden", emptySet())!!
        val pins = prefs.getStringSet("pinned", emptySet())!!
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(i, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName && it.second !in hidden }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        pinnedBox.removeAllViews()
        apps.filter { it.second in pins }.forEach { a ->
            pinnedBox.addView(TextView(this).apply {
                text = a.first
                textSize = 22f
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 16)
                setOnClickListener { launch(a.second) }
                setOnLongClickListener { menu(a); true }
            })
        }
        filter()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        shown = apps.filter { it.first.lowercase().contains(q) }
        adapter.clear()
        adapter.addAll(shown.map { it.first })
    }

    private fun menu(a: Pair<String, String>) {
        val pins = prefs.getStringSet("pinned", emptySet())!!.toMutableSet()
        val pinned = a.second in pins
        AlertDialog.Builder(this)
            .setItems(arrayOf(if (pinned) "Bỏ ghim" else "Ghim", "Ẩn", "Gỡ cài đặt")) { _, w ->
                when (w) {
                    0 -> {
                        if (pinned) pins.remove(a.second) else pins.add(a.second)
                        prefs.edit().putStringSet("pinned", pins).apply()
                    }
                    1 -> {
                        val h = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()
                        h.add(a.second)
                        prefs.edit().putStringSet("hidden", h).apply()
                    }
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.second}")))
                }
                refresh()
            }.show()
    }
}
