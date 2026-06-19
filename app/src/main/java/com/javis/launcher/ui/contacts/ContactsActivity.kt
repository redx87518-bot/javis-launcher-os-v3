package com.javis.launcher.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.models.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 200)
        }
    }

    private fun loadContacts() {
        val rvFrequent = findViewById<RecyclerView>(R.id.rv_frequent)
        val rvAll = findViewById<RecyclerView>(R.id.rv_all_contacts)
        rvFrequent.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAll.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val topContacts = memory.getTopContacts(6)
            val favoriteIds = topContacts.map { it.contactId }.toSet()

            val allContacts = withContext(Dispatchers.IO) { readAllContacts() }

            // Frequent
            val frequentContacts = allContacts.filter { it.id in favoriteIds }
            rvFrequent.adapter = ContactAdapter(frequentContacts, horizontal = true) { call(it) }

            // All
            rvAll.adapter = ContactAdapter(allContacts, horizontal = false) { call(it) }
        }
    }

    private fun readAllContacts(): List<Contact> {
        val list = mutableListOf<Contact>()
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, proj, null, null,
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { c ->
            val idIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = mutableSetOf<String>()
            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                if (seen.add(id)) {
                    list.add(Contact(id, c.getString(nameIdx), c.getString(phoneIdx)))
                }
            }
        }
        return list
    }

    private fun call(contact: Contact) {
        memory.trackContactCall(contact)
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phone}"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 201)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadContacts()
    }
}

class ContactAdapter(
    private val contacts: List<Contact>,
    private val horizontal: Boolean,
    private val onCall: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_contact_name)
        val tvPhone: TextView = v.findViewById(R.id.tv_contact_phone)
        val tvInitial: TextView = v.findViewById(R.id.tv_contact_initial)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (horizontal) R.layout.item_contact_card else R.layout.item_contact_row
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = contacts[position]
        holder.tvName.text = c.name
        holder.tvPhone.text = c.phone
        holder.tvInitial.text = c.name.firstOrNull()?.uppercase() ?: "?"
        holder.itemView.setOnClickListener { onCall(c) }
    }

    override fun getItemCount() = contacts.size
}
