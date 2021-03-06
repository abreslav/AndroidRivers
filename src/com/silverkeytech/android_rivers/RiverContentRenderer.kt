/*
Android Rivers is an app to read and discover news using RiverJs, RSS and OPML format.
Copyright (C) 2012 Dody Gunawinata (dodyg@silverkeytech.com)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>
*/

package com.silverkeytech.android_rivers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.silverkeytech.android_rivers.riverjs.FeedItemMeta

//Manage the rendering of each news item in the river list
public class RiverContentRenderer(val context: Activity){
    class object {
        val STANDARD_NEWS_COLOR = android.graphics.Color.GRAY
        val STANDARD_NEWS_IMAGE = android.graphics.Color.CYAN
        val STANDARD_NEWS_PODCAST = android.graphics.Color.MAGENTA

        public val TAG: String = javaClass<RiverContentRenderer>().getSimpleName()
    }

    //hold the view data for the list
    public data class ViewHolder (var news: TextView, val indicator: TextView)


    //show and prepare the interaction for each individual news item
    fun handleNewsListing(sortedNewsItems: List<FeedItemMeta>) {
        val textSize = context.getVisualPref().getListTextSize()

        //now sort it so people always have the latest news first

        var list = context.findView<ListView>(android.R.id.list)

        var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        var adapter = object : ArrayAdapter<FeedItemMeta>(context, R.layout.news_item, sortedNewsItems) {
            public override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
                var currentView = convertView
                var holder: ViewHolder?

                var currentNewsItem = sortedNewsItems[position]
                var news = currentNewsItem.item.toString()?.trim()

                if (news == null)
                    news = ""

                fun showIndicator() {
                    if (currentNewsItem.item.containsEnclosure()!!){
                        var enclosure = currentNewsItem.item.enclosure!!.get(0)!!
                        if (isSupportedImageMime(enclosure.`type`!!)){
                            holder?.indicator?.setBackgroundColor(STANDARD_NEWS_IMAGE)
                        }
                        else{
                            holder?.indicator?.setBackgroundColor(STANDARD_NEWS_PODCAST)
                        }
                    }else{
                        holder?.indicator?.setBackgroundColor(STANDARD_NEWS_COLOR)
                    }
                }

                if (currentView == null){
                    currentView = inflater.inflate(R.layout.news_item, parent, false)

                    holder = ViewHolder(currentView!!.findViewById(R.id.news_item_text_tv) as TextView,
                            currentView!!.findViewById(R.id.news_item_indicator_tv) as TextView)

                    holder?.news?.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                    currentView!!.setTag(holder)
                }else{
                    holder = currentView?.getTag() as ViewHolder
                    Log.d(TAG, "List View reused")
                }

                holder?.news?.setText(news)
                showIndicator()

                if (news.isNullOrEmpty()){
                    currentView?.setVisibility(View.GONE)
                }   else{
                    currentView?.setVisibility(View.VISIBLE)
                }
                return currentView
            }
        }

        list.setOnItemClickListener(object : OnItemClickListener{
            public override fun onItemClick(p0: AdapterView<out Adapter?>?, p1: View?, p2: Int, p3: Long) {
                val currentNews = sortedNewsItems.get(p2);

                var dialog = AlertDialog.Builder(context)


                var dlg : View = inflater.inflate(R.layout.news_details, null)!!
                var msg : String

                if (currentNews.item.body.isNullOrEmpty() && !currentNews.item.title.isNullOrEmpty())
                    msg = scrubHtml(currentNews.item.title)
                else
                    msg = scrubHtml(currentNews.item.body)

                var body = dlg.findViewById(R.id.news_details_text_tv)!! as TextView
                body.setText(msg)
                body.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())

                var source = dlg.findViewById(R.id.news_details_source_tv)!! as TextView
                source.setText(scrubHtml(currentNews.feedSourceTitle))
                source.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.toFloat())

                dialog.setView(dlg)


                val currentLink = currentNews.item.link
                val currentNewsLinkAvailable: Boolean = !currentLink.isNullOrEmpty() && currentLink!!.trim().indexOf("http") == 0

                //check for go link
                if (currentNewsLinkAvailable){
                    dialog.setNeutralButton("Go", object : DialogInterface.OnClickListener{
                        public override fun onClick(p0: DialogInterface?, p1: Int) {
                            var i = Intent("android.intent.action.VIEW", Uri.parse(currentNews.item.link))
                            context.startActivity(i)
                            p0?.dismiss()
                        }
                    })
                }

                //check for image enclosure
                if (currentNews.item.containsEnclosure()!! ){
                    var enclosure = currentNews.item.enclosure!!.get(0)!!
                    if (isSupportedImageMime(enclosure.`type`!!)){
                        dialog.setNegativeButton("Image", object : DialogInterface.OnClickListener{
                            public override fun onClick(p0: DialogInterface?, p1: Int) {
                                Log.d(TAG, "I am downloading a ${enclosure.url} with type ${enclosure.`type`}")
                                DownloadImage(context).execute(enclosure.url)
                            }
                        })
                    }
                    //assume podcast
                    else {
                        dialog.setNegativeButton("Podcast", object : DialogInterface.OnClickListener{
                            public override fun onClick(p0: DialogInterface?, p1: Int) {
                                var messenger = Messenger(object : Handler(){
                                    public override fun handleMessage(msg: Message?) {
                                        if (msg != null){
                                            val path = msg.obj as String

                                            if (msg.arg1 == Activity.RESULT_OK && !path.isNullOrEmpty()){
                                                context.toastee("File is successfully downloaded at $path", Duration.LONG)
                                                MediaScannerWrapper.scanPodcasts(context, path)
                                            }else{
                                                context.toastee("Download failed", Duration.LONG)
                                            }
                                        }else{
                                            Log.d(TAG, "handleMessage returns null, which is very weird")
                                        }
                                    }
                                })

                                var intent = Intent(context, javaClass<DownloadService>())
                                intent.putExtra(DownloadService.PARAM_DOWNLOAD_URL, enclosure.url)
                                intent.putExtra(DownloadService.PARAM_DOWNLOAD_TITLE, currentNews.item.title)
                                intent.putExtra(Params.MESSENGER, messenger)
                                context.startService(intent)
                            }
                        })
                    }
                }else{
                    //there is not enclosure detected, so enable sharing
                    if (currentNewsLinkAvailable) {
                        dialog.setNegativeButton("Share", object : DialogInterface.OnClickListener{
                            public override fun onClick(p0: DialogInterface?, p1: Int) {
                                var intent = Intent(Intent.ACTION_SEND)
                                intent.setType("text/plain")
                                intent.putExtra(Intent.EXTRA_TEXT, currentNews.item.link!!)
                                context.startActivity(Intent.createChooser(intent, "Share page"))
                            }
                        })
                    }
                }

                var createdDialog = dialog.create()!!
                createdDialog.setCanceledOnTouchOutside(true)
                dlg.setOnClickListener {
                    createdDialog.dismiss()
                }

                createdDialog.show()
            }
        })

        list.setAdapter(adapter)
    }
}