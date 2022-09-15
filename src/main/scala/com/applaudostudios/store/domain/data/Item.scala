package com.applaudostudios.store.domain.data

case class Item(id: Int, brand: String, categoryId: BigInt, price: Double = 0.0)
case class InputItem(id: Int, brand: String, price: Double)
