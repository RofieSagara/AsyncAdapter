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

abstract class AsyncAdapter<T: BaseModel<T>, Y: RecyclerView.ViewHolder>: RecyclerView.Adapter<Y>() {
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
      return this@AsyncAdapter.areContentsTheSame(oldItem, newItem)
    }

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
      return this@AsyncAdapter.areItemsTheSame(oldItem, newItem)
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
      val finalList = onInit(cList)
      withContext(Dispatchers.Main){
        submitListSync(finalList)
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
    return other
  }

  /**
   * Override this suspend function to make init to all List.
   * This function run on Async so don't worry to make heavy logic
   * items in this params already clean and sort
   *
   * Tips: You can do init model, For example if you have model to show in RecycleView and in that
   * model have property String of DateTime and you need to convert to other type like "just now",
   * 5 minutes later or you change with other for of DateTime you can map list in here.
   * so you can make your onBindViewHolder only put string to view without logic to make onBindViewHolder more fast.
   *
   * @return Return of list of T, the list will put in RecycleView make sure not mess with clean or Sort
   */
  protected open suspend fun onInit(items: List<T>): List<T>{
    return items
  }

  /**
   * Get current list read Only List
   */
  val items: List<T> get() = mData.currentList

  /**
   * Called to check whether two items have the same data.
   * <p>
   * This information is used to detect if the contents of an item have changed.
   * <p>
   * This method to check equality instead of {@link Object#equals(Object)} so that you can
   * change its behavior depending on your UI.
   * <p>
   * For example, if you are using DiffUtil with a
   * {@link RecyclerView.Adapter RecyclerView.Adapter}, you should
   * return whether the items' visual representations are the same.
   * <p>
   * This method is called only if {@link #areItemsTheSame(T, T)} returns {@code true} for
   * these items.
   * <p>
   * Note: Two {@code null} items are assumed to represent the same contents. This callback
   * will not be invoked for this case.
   *
   * @param oldItem The item in the old list.
   * @param newItem The item in the new list.
   * @return True if the contents of the items are the same or false if they are different.
   *
   */
  protected abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean

  /**
   * Called to check whether two objects represent the same item.
   * <p>
   * For example, if your items have unique ids, this method should check their id equality.
   * <p>
   * Note: {@code null} items in the list are assumed to be the same as another {@code null}
   * item and are assumed to not be the same as a non-{@code null} item. This callback will
   * not be invoked for either of those cases.
   *
   * @param oldItem The item in the old list.
   * @param newItem The item in the new list.
   * @return True if the two items represent the same object or false if they are different.
   *
   */
  protected abstract fun areItemsTheSame(oldItem: T, newItem: T): Boolean

  private suspend fun submitListSync(items: List<T>): Boolean = suspendCoroutine {
    Log.d("AsyncAdapter","Submit ${items.size} of items")
    mData.submitList(items.freshCopy()) {
      it.resume(true)
    }
  }

  /**
   * Add list of items to adapter this items not replace item. but join then item and replace the same
   */
  fun add(items: List<T>){
    mWorm.wormAdd(items)
  }

  /**
   * Remove item from adapter
   */
  fun remove(item: T){
    mWorm.wormRemove(item)
  }

  /**
   * Clear the Adapter
   */
  fun clear(){
    mWorm.wormClear()
  }

  /**
   * Because this run Async so you don't know when the progress done. you can use run this function to run specific block
   * Example: you call add(items: List) and after that you call this function
   * it's mean this function will call after add function done!
   */
  fun run(block: suspend ()-> Unit){
    mWorm.wormRun(block)
  }

  override fun getItemId(position: Int): Long {
    return position.toLong()
  }

  override fun getItemCount(): Int {
    return mData.currentList.size
  }
}