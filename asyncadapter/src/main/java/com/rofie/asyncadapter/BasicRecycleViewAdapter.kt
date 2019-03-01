/*
 * Copyright 2018 Rofie Sagara.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.rofie.asyncadapter

import android.util.Log
import androidx.recyclerview.widget.*
import com.rofie.asyncadapter.model.BaseModel
import com.rofie.asyncadapter.worm.WormAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

//
// Created by ROFIE SAGARA on 11/18/2018.
// Copyright (c) 2018 Tamvan Developer. All rights reserved.
//

abstract class BasicRecycleViewAdapter<T: BaseModel<T>, Y: RecyclerView.ViewHolder>: RecyclerView.Adapter<Y>() {
  private val mListUpdateCallback = object: ListUpdateCallback{
    override fun onChanged(position: Int, count: Int, payload: Any?) {
      notifyItemRangeChanged(position, count, payload)
    }

    override fun onInserted(position: Int, count: Int) {
      notifyItemRangeInserted(position, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
      notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRemoved(position: Int, count: Int) {
      notifyItemRangeRemoved(position, count)
    }
  }

  private val mData = AsyncListDiffer<T>(mListUpdateCallback, AsyncDifferConfig.Builder<T>(object : DiffUtil.ItemCallback<T>(){
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
      return this@BasicRecycleViewAdapter.areContentsTheSame(oldItem, newItem)
    }

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
      return this@BasicRecycleViewAdapter.areItemsTheSame(oldItem, newItem)
    }
  }).build())

  private val mWorm = object : WormAsync<T>(){
    override suspend fun onInsert(items: List<T>) {
      val cList = mData.currentList.toMutableList()
      val leftList: MutableList<T?> = items.toMutableList()
      if(cList.isNotEmpty()){
        val fresh = cList.map { t ->
          var selectItem = t
          items.forEachIndexed { index, it ->
            if(t.findProperty(it)) {
              leftList[index] = null
              Log.d("AsyncAdapter","Found item to replace $t ==> $it")
              selectItem = onReplace(t, it)
              return@forEachIndexed
            }
          }
          return@map selectItem
        }.toList()
        val cleanLeft = leftList.filter { it != null }.toList()
        cList.clear()
        cList.addAll(fresh)
        cList.addAll(cleanLeft)
      }else{
        cList.addAll(items)
      }
      comparator().notNull {
        Log.d("AsyncAdapter","Comparator found, Start Sort with Comparator")
        cList.sortWith(it)
      }
      withContext(Dispatchers.Main){
        submitListSync(cList)
      }
    }

    override suspend fun onRemove(items: T) {
      val cList = mData.currentList.toMutableList()
      val fresh = cList.map {
        if(it.findProperty(items)){
          return@map null
        }else{
          return@map it
        }
      }.filter { it != null }.toList()
      cList.clear()
      cList.addAll(fresh)
      withContext(Dispatchers.Main){
        submitListSync(cList)
      }
    }

    override suspend fun onClear() {
      withContext(Dispatchers.Main){
        submitListSync(listOf())
      }
    }
  }

  /**
   * Override this function to make Sort with Comparator
   * @return the Comparator
   */
  protected open fun comparator(): Comparator<in T>?{
    return null
  }

  /**
   * Override this function to make Replace when item found in the new List
   * @return Select from 2 of them or u can make specific object from them
   */
  protected open fun onReplace(current: T, other: T): T{
    return current
  }

  /**
   * Get current list read Only List
   */
  protected val items: List<T> get() = mData.currentList

  protected abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean

  protected abstract fun areItemsTheSame(oldItem: T, newItem: T): Boolean

  private suspend fun submitListSync(items: List<T>): Boolean = suspendCoroutine {
    Log.d("AsyncAdapter","Submit ${items.size} of items")
    mData.submitList(items.freshCopy()) {
      it.resume(true)
    }
  }

  fun add(lData: List<T>){
    mWorm.wormAdd(lData)
  }

  fun remove(data: T){
    mWorm.wormRemove(data)
  }

  fun clear(){
    mWorm.wormClear()
  }

  override fun getItemId(position: Int): Long {
    return position.toLong()
  }

  override fun getItemCount(): Int {
    return mData.currentList.size
  }
}