/*
 * Copyright 2019 Rofie Sagara.
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


package com.rofie.asyncadapter.worm

import com.rofie.asyncadapter.freshCopy
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

//
// Created by ROFIE SAGARA on 2/25/2019.
// Copyright (c) 2019 Tamvan Developer. All rights reserved.
//
abstract class WormAsync<T> {
  private val stockOrganic = mutableListOf<Organic<T>>()
  private var mInsect: Job = GlobalScope.launch {
    while (stockOrganic.size > 0) {
      val food = stockOrganic[0]
      eat(food)
      if(stockOrganic.size > 0){
        stockOrganic.removeAt(0)
      }
    }
  }

  private suspend fun eat(food: Organic<T>){
    when(food.code){
      INSERT -> {
        if (food.items != null) onInsert(food.items.freshCopy())
      }
      REMOVE -> {
        if (food.items != null) food.items.forEach { onRemove(it) }
      }
      CLEAR -> {
        onClear()
      }
      RUN -> {
        onRun(food.block)
      }
    }
  }

  fun wormRun(block: suspend () -> Unit){
    stockOrganic.add(
      Organic(RUN, block = block)
    )
    wormMe()
  }

  fun wormAdd(data: List<T>){
    stockOrganic.add(
      Organic(
        INSERT,
        data.freshCopy()
      )
    )
    wormMe()
  }

  fun wormRemove(data: T){
    stockOrganic.add(
      Organic(
        REMOVE,
        listOf(data)
      )
    )
    wormMe()
  }

  fun wormClear(){
    stockOrganic.add(
      Organic(CLEAR)
    )
    wormMe()
  }

  fun wormKill(){
    mInsect.cancel()
  }

  private fun wormMe() {
    if (mInsect.isCompleted) {
      mInsect = GlobalScope.launch {
        while (stockOrganic.size > 0) {
          val food = stockOrganic[0]
          eat(food)
          if(stockOrganic.size > 0){
            stockOrganic.removeAt(0)
          }
        }
      }
    }
  }

  protected abstract suspend fun onInsert(items: List<T>)

  protected abstract suspend fun onRemove(items: T)

  protected abstract suspend fun onClear()

  private suspend fun onRun(block: suspend ()-> Unit){
    block()
  }

  internal class Organic<T>(val code: Int, val items: List<T>? = null, var block: suspend () -> Unit = {})

  companion object {
    private const val INSERT = 1
    private const val REMOVE = 2
    private const val CLEAR = 3
    private const val RUN = 4
  }
}