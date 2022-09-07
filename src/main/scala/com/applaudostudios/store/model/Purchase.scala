package com.applaudostudios.store.model

import java.time.LocalDate


case class Purchase(
                     eventTime:LocalDate,
                     productId:String,
                     categoryId:String,
                     categoryCode:String,
                     brand:String,
                     price:Double,
                     userId:String,
                     userSession:String
                   )
