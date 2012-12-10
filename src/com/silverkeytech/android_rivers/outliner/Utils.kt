package com.silverkeytech.android_rivers.outliner

import com.silverkeytech.android_rivers.outlines.Opml
import java.util.ArrayList
import com.silverkeytech.android_rivers.outlines.Outline
import com.silverkeytech.android_rivers.Result
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.Serializer
import android.util.Log

//do an in order traversal so we can flatten it up to be used by outliner
fun Opml.traverse (filter : ((Outline) -> Boolean)? = null) : ArrayList<Pair<Int, String>>{
    var list = ArrayList<Pair<Int,String>>()

    var level = 0
    for (val o in this.body?.outline?.iterator())    {
        traverseOutline(level, o, list, filter)
    }
    return list
}

private fun traverseOutline(level : Int, outline : Outline?, list : ArrayList<Pair<Int, String>>, filter : ((Outline) -> Boolean)?){
    if (outline != null){
        val proceed = filter != null && filter(outline)

        if (proceed){
            list.add(Pair(level, outline.text!!))

            var lvl = level
            lvl++

            for(val o in outline.outline?.iterator()){
                traverseOutline(lvl, o, list, filter)
            }
        }
    }
}

fun transformXmlToOpml(xml: String?): Result<Opml> {
    var serial: Serializer = Persister()

    try{
        val opml: Opml? = serial.read(javaClass<Opml>(), xml, false)
        Log.d("OPML Transform", "OPML ${opml?.head?.title} created on ${opml?.head?.getDateCreated()} and modified on ${opml?.head?.getDateModified()}")
        return Result.right(opml)
    }
    catch (e: Exception){
        Log.d("OPML Transform", "Exception ${e.getMessage()}")
        return Result.wrong(e)
    }
}