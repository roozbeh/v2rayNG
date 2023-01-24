package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R


class CodeDetail(val code_url: String, var guid: String, var code_ping: String) {

    companion object {
        private var lastContactId = 0
        const val kLoading = "loading"

        fun createEmptyCodeList(): ArrayList<CodeDetail> {
            val codes = ArrayList<CodeDetail>()
            codes.add(CodeDetail("Codes Loading " , "", kLoading))
            return codes
        }
    }
}

class CodeRecyclerAdapter(private val mCodes: List<CodeDetail>)  : RecyclerView.Adapter<CodeRecyclerAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row
        val codeNameView = itemView.findViewById<TextView>(R.id.code_name)
        val codePingView = itemView.findViewById<TextView>(R.id.code_ping_time)
    }

    // ... constructor and member variables
    // Usually involves inflating a layout from XML and returning the holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodeRecyclerAdapter.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        // Inflate the custom layout
        val contactView = inflater.inflate(R.layout.item_code, parent, false)
        // Return a new holder instance
        return ViewHolder(contactView)
    }

    // Involves populating data into the item through holder
    override fun onBindViewHolder(viewHolder: CodeRecyclerAdapter.ViewHolder, position: Int) {
        // Get the data model based on position
        val contact: CodeDetail = mCodes.get(position)
        // Set item views based on your views and data model
        viewHolder.codeNameView.setText(contact.code_url)
        viewHolder.codePingView.setText(contact.code_ping)
    }

    // Returns the total count of items in the list
    override fun getItemCount(): Int {
        return mCodes.size
    }
}
