package com.example.mcmilkywayidle

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScriptManagerActivity : AppCompatActivity() {
    private var scriptManager: ScriptManager? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: ScriptAdapter? = null
    private val scripts: MutableList<ScriptManager.ScriptInfo> =
        ArrayList<ScriptManager.ScriptInfo>()

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_manager)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        getSupportActionBar()!!.setDisplayHomeAsUpEnabled(true)
        getSupportActionBar()!!.setTitle("Script Manager")

        scriptManager = ScriptManager(this)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView!!.setLayoutManager(LinearLayoutManager(this))

        adapter = ScriptAdapter()
        recyclerView!!.setAdapter(adapter)

        val addUrlButton: Button = findViewById(R.id.add_url_button)
        val addCustomButton: Button = findViewById(R.id.add_custom_button)

        addUrlButton.setOnClickListener { v: View? -> showAddUrlDialog() }
        addCustomButton.setOnClickListener { v: View? -> showAddCustomDialog() }

        loadScripts()


        // Update all scripts when opening the manager
        scriptManager!!.updateAllScripts { this.loadScripts() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadScripts() {
        scripts.clear()
        scripts.addAll(scriptManager!!.allScripts())
        adapter!!.notifyDataSetChanged()
    }

    private fun showAddUrlDialog() {
        val builder = AlertDialog.Builder(this)
        val view: View = getLayoutInflater().inflate(R.layout.dialog_add_script_url, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val urlInput = view.findViewById<EditText>(R.id.script_url)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)
        val autoUpdateCheckbox = view.findViewById<CheckBox>(R.id.script_auto_update)

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
            val autoUpdate = autoUpdateCheckbox.isChecked

            // Pattern for URLs like https://greasyfork.org/{lang}/scripts/{number}-{scriptname}
            val baseUrlPattern = """https://greasyfork\.org/[^/]+/scripts/\d+(?:-[^/]+)?$""".toRegex()

            // Pattern for URLs like https://greasyfork.org/{lang}/scripts/{number}-{scriptname}/code
            val codeUrlPattern = """https://greasyfork\.org/[^/]+/scripts/\d+(?:-[^/]+)?/code$""".toRegex()

            // Modify URL based on the pattern
            val modifiedUrl = when {
                baseUrlPattern.matches(url) -> "$url/code/script.user.js"
                codeUrlPattern.matches(url) -> "$url/script.user.js"
                else -> url
            }

            // Validate that URL ends with .js
            if (!modifiedUrl.endsWith(".js")) {
                // Show error message
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


            // Show loading toast
            Toast.makeText(
                this@ScriptManagerActivity,
                "Downloading script...",
                Toast.LENGTH_SHORT
            ).show()
            scriptManager!!.addScriptFromUrl(name, modifiedUrl, enabled, autoUpdate) { success ->
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

    private fun showAddCustomDialog() {
        val builder = AlertDialog.Builder(this)
        val view: View = getLayoutInflater().inflate(R.layout.dialog_add_custom_script, null)

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
            scriptManager!!.addCustomScript(name, content, enabled) { success ->
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

    private fun showEditScriptDialog(script: ScriptManager.ScriptInfo) {
        if (script.isCustom) {
            showEditCustomScriptDialog(script)
        } else {
            showEditUrlScriptDialog(script)
        }
    }

    private fun showEditUrlScriptDialog(script: ScriptManager.ScriptInfo) {
        val builder = AlertDialog.Builder(this)
        val view: View = getLayoutInflater().inflate(R.layout.dialog_add_script_url, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val urlInput = view.findViewById<EditText>(R.id.script_url)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)
        val autoUpdateCheckbox = view.findViewById<CheckBox>(R.id.script_auto_update)

        nameInput.setText(script.name)
        urlInput.setText(script.url)
        enabledCheckbox.isChecked = script.isEnabled
        autoUpdateCheckbox.isChecked = script.isAutoUpdate

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
                        scriptManager!!.removeScript(script.filename)
                        loadScripts()
                        Toast.makeText(
                            this@ScriptManagerActivity,
                            "Script deleted",
                            Toast.LENGTH_SHORT
                        ).show()
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
            val autoUpdate = autoUpdateCheckbox.isChecked

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(
                    this@ScriptManagerActivity,
                    "Name and URL are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }


            // Delete old script and add new one with updated info
            scriptManager!!.removeScript(script.filename)
            scriptManager!!.addScriptFromUrl(name, url, enabled, autoUpdate) { success ->
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

    private fun showEditCustomScriptDialog(script: ScriptManager.ScriptInfo) {
        val builder = AlertDialog.Builder(this)
        val view: View = getLayoutInflater().inflate(R.layout.dialog_add_custom_script, null)

        val nameInput = view.findViewById<EditText>(R.id.script_name)
        val contentInput = view.findViewById<EditText>(R.id.script_content)
        val enabledCheckbox = view.findViewById<CheckBox>(R.id.script_enabled)

        nameInput.setText(script.name)
        contentInput.setText(scriptManager!!.loadScriptContent(script.filename))
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
                        scriptManager!!.removeScript(script.filename)
                        loadScripts()
                        Toast.makeText(
                            this@ScriptManagerActivity,
                            "Script deleted",
                            Toast.LENGTH_SHORT
                        ).show()
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


            // Delete old script and add new one with updated info
            scriptManager!!.removeScript(script.filename)
            scriptManager!!.addCustomScript(name, content, enabled) { success ->
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

    private inner class ScriptAdapter : RecyclerView.Adapter<ScriptAdapter.ViewHolder?>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_script, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return scripts.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val script: ScriptManager.ScriptInfo = scripts[position]

            holder.nameText.setText(script.name)
            holder.typeText.text = if (script.isCustom) "Custom Script" else "URL Script"

            if (script.isCustom) {
                holder.urlText.visibility = View.GONE
                holder.autoUpdateSwitch.visibility = View.GONE
            } else {
                holder.urlText.visibility = View.VISIBLE
                holder.autoUpdateSwitch.visibility = View.VISIBLE
                holder.urlText.setText(script.url)
                holder.autoUpdateSwitch.isChecked = script.isAutoUpdate
            }

            holder.enabledSwitch.isChecked = script.isEnabled

            holder.enabledSwitch.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                scriptManager!!.setScriptEnabled(script.filename, isChecked)
            }

            holder.autoUpdateSwitch.setOnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean ->
                // We'll need to update the script info and save it
                scriptManager!!.removeScript(script.filename)
                scriptManager!!.addScriptFromUrl(
                    script.name, script.url,
                    script.isEnabled, isChecked
                ) { success ->
                    if (!success) {
                        Toast.makeText(
                            this@ScriptManagerActivity,
                            "Failed to update auto-update setting", Toast.LENGTH_SHORT
                        ).show()
                        buttonView.isChecked = !isChecked
                        loadScripts()
                    }
                }
            }

            holder.itemView.setOnClickListener { v ->
                showEditScriptDialog(script)
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var nameText: TextView = view.findViewById(R.id.script_name)
            var typeText: TextView = view.findViewById(R.id.script_type)
            var urlText: TextView = view.findViewById(R.id.script_url)
            var enabledSwitch: Switch = view.findViewById(R.id.script_enabled)
            var autoUpdateSwitch: Switch = view.findViewById(R.id.script_auto_update)
        }
    }
}