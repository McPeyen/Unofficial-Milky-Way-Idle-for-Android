package com.mcpeyen.unofficialmilkywayidle

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date

class ScriptManagerActivity : AppCompatActivity() {
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScriptAdapter
    private val scripts: MutableList<UserScriptManager.ScriptInfo> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        setContentView(R.layout.activity_script_manager)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Script Manager"

        userScriptManager = UserScriptManager(this, lifecycleScope)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ScriptAdapter()
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition

                Collections.swap(scripts, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                lifecycleScope.launch {
                    userScriptManager.saveScriptOrder(scripts)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val addUrlButton: Button = findViewById(R.id.add_url_button)
        val addCustomButton: Button = findViewById(R.id.add_custom_button)

        addUrlButton.setOnClickListener { showAddUrlDialog() }
        addCustomButton.setOnClickListener { showAddCustomDialog() }

        lifecycleScope.launch {
            userScriptManager.updateEnabledScripts {
                loadScripts()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) {
            return "Last updated: Never"
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm")
        return "Last updated: " + sdf.format(Date(timestamp))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun loadScripts() {
        scripts.clear()
        scripts.addAll(userScriptManager.getAllScripts())
        adapter.notifyDataSetChanged()
    }

    private fun showAddUrlDialog() {
        val builder = AlertDialog.Builder(this)
        val view: View = layoutInflater.inflate(R.layout.dialog_add_script_url, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val urlInput = view.findViewById<EditText>(R.id.script_url)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)

        builder.setView(view)
            .setTitle("Add Script from URL")
            .setPositiveButton("Add", null)
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, id: Int -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            val name = nameInput.text.toString().trim { it <= ' ' }
            val url = urlInput.text.toString().trim { it <= ' ' }
            val enabled = enabledCheckbox.isChecked

            val baseUrlPattern =
                """https://greasyfork\.org/[^/]+/scripts/\d+(?:-[^/]+)?$""".toRegex()

            val codeUrlPattern =
                """https://greasyfork\.org/[^/]+/scripts/\d+(?:-[^/]+)?/code$""".toRegex()

            val modifiedUrl = when {
                baseUrlPattern.matches(url) -> "$url/code/script.user.js"
                codeUrlPattern.matches(url) -> "$url/script.user.js"
                else -> url
            }

            if (!modifiedUrl.endsWith(".js")) {
                urlInput.error = "URL must end with .js"
                return@setOnClickListener
            }

            if (name.isEmpty() || modifiedUrl.isEmpty()) {
                Toast.makeText(
                    this@ScriptManagerActivity,
                    "Name and URL are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            Toast.makeText(
                this@ScriptManagerActivity,
                "Downloading script...",
                Toast.LENGTH_SHORT
            ).show()
            lifecycleScope.launch {
                val success = userScriptManager.addScriptFromUrl(name, modifiedUrl, enabled)

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            this@ScriptManagerActivity,
                            "Script added successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        lifecycleScope.launch {
                            loadScripts()
                        }
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            this@ScriptManagerActivity,
                            "Failed to add script",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showAddCustomDialog() {
        val builder = AlertDialog.Builder(this)
        val view: View = layoutInflater.inflate(R.layout.dialog_add_custom_script, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val contentInput = view.findViewById<EditText>(R.id.script_content)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)

        builder.setView(view)
            .setTitle("Add Custom Script")
            .setPositiveButton("Add", null)
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, id: Int -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            val name = nameInput.text.toString().trim { it <= ' ' }
            val content = contentInput.text.toString().trim { it <= ' ' }
            val enabled = enabledCheckbox.isChecked

            if (name.isEmpty() || content.isEmpty()) {
                Toast.makeText(
                    this@ScriptManagerActivity,
                    "Name and script content are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val success = userScriptManager.addCustomScript(name, content, enabled)

                if (success) {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Script added successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScripts()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Failed to add script",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showEditScriptDialog(script: UserScriptManager.ScriptInfo) {
        if (script.isCustom) {
            showEditCustomScriptDialog(script)
        } else {
            showEditUrlScriptDialog(script)
        }
    }

    private fun showEditUrlScriptDialog(script: UserScriptManager.ScriptInfo) {
        val builder = AlertDialog.Builder(this)
        val view: View = layoutInflater.inflate(R.layout.dialog_add_script_url, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val urlInput = view.findViewById<EditText>(R.id.script_url)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)

        nameInput.setText(script.name)
        urlInput.setText(script.url)
        enabledCheckbox.isChecked = script.isEnabled

        builder.setView(view)
            .setTitle("Edit Script")
            .setPositiveButton("Save", null)
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, id: Int -> dialog.dismiss() }
            .setNeutralButton(
                "Delete"
            ) { dialog: DialogInterface?, id: Int ->
                AlertDialog.Builder(this@ScriptManagerActivity)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete this script?")
                    .setPositiveButton("Yes") { d: DialogInterface?, which: Int ->
                        // Launch coroutine to call suspend functions
                        lifecycleScope.launch {
                            userScriptManager.removeScript(script.filename)
                            loadScripts()

                            Toast.makeText(
                                this@ScriptManagerActivity,
                                "Script deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            val name = nameInput.text.toString().trim { it <= ' ' }
            val url = urlInput.text.toString().trim { it <= ' ' }
            val enabled = enabledCheckbox.isChecked

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(
                    this@ScriptManagerActivity,
                    "Name and URL are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                userScriptManager.removeScript(script.filename)

                val success = userScriptManager.addScriptFromUrl(name, url, enabled)
                if (success) {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Script updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScripts()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Failed to update script",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showEditCustomScriptDialog(script: UserScriptManager.ScriptInfo) {
        val builder = AlertDialog.Builder(this)
        val view: View = layoutInflater.inflate(R.layout.dialog_add_custom_script, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val contentInput = view.findViewById<EditText>(R.id.script_content)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)

        nameInput.setText(script.name)
        lifecycleScope.launch {
            contentInput.setText(userScriptManager.loadScriptContent(script.filename))
        }
        enabledCheckbox.isChecked = script.isEnabled

        builder.setView(view)
            .setTitle("Edit Custom Script")
            .setPositiveButton("Save", null)
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, id: Int -> dialog.dismiss() }
            .setNeutralButton(
                "Delete"
            ) { dialog: DialogInterface?, id: Int ->
                AlertDialog.Builder(this@ScriptManagerActivity)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete this script?")
                    .setPositiveButton("Yes") { d: DialogInterface?, which: Int ->
                        lifecycleScope.launch {
                            userScriptManager.removeScript(script.filename)
                            loadScripts()
                            Toast.makeText(
                                this@ScriptManagerActivity,
                                "Script deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            val name = nameInput.text.toString().trim { it <= ' ' }
            val content = contentInput.text.toString().trim { it <= ' ' }
            val enabled = enabledCheckbox.isChecked

            if (name.isEmpty() || content.isEmpty()) {
                Toast.makeText(
                    this@ScriptManagerActivity,
                    "Name and script content are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                userScriptManager.removeScript(script.filename)

                val success = userScriptManager.addCustomScript(name, content, enabled)
                if (success) {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Script updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScripts()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this@ScriptManagerActivity,
                        "Failed to update script",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun performUpdate(script: UserScriptManager.ScriptInfo) {
        if (script.isCustom || script.url.isEmpty()) {
            Toast.makeText(this, "Cannot update custom scripts", Toast.LENGTH_SHORT).show()
            return
        }

        val index = scripts.indexOf(script)
        if (index == -1) return

        Toast.makeText(this, "Updating ${script.name}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val success = userScriptManager.updateScriptContentFromUrl(script, index)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@ScriptManagerActivity, "Updated successfully", Toast.LENGTH_SHORT).show()
                    loadScripts()
                } else {
                    Toast.makeText(this@ScriptManagerActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class ScriptAdapter : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_script, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = scripts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val script = scripts[position]

            holder.nameText.text = script.name
            holder.typeText.text = if (script.isCustom) "Custom Script" else "URL Script"
            holder.lastUpdatedText.text = formatTimestamp(script.lastUpdated)

            if (script.isCustom) {
                holder.urlText.visibility = View.GONE
                holder.updateButton.visibility = View.GONE
            } else {
                holder.urlText.visibility = View.VISIBLE
                holder.urlText.text = script.url
                holder.updateButton.visibility = View.VISIBLE
            }

            holder.enabledSwitch.setOnCheckedChangeListener(null) // Clear listener to prevent recycling bugs
            holder.enabledSwitch.isChecked = script.isEnabled
            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    userScriptManager.setScriptEnabled(script.filename, isChecked)
                    script.isEnabled = isChecked
                }
            }

            holder.updateButton.setOnClickListener {
                performUpdate(script)
            }

            holder.itemView.setOnClickListener {
                showEditScriptDialog(script)
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.script_name)
            val typeText: TextView = view.findViewById(R.id.script_type)
            val urlText: TextView = view.findViewById(R.id.script_url)
            val lastUpdatedText: TextView = view.findViewById(R.id.script_last_updated)
            val enabledSwitch: Switch = view.findViewById(R.id.script_enabled)
            val updateButton: ImageButton = view.findViewById(R.id.script_update_button)
        }
    }
}