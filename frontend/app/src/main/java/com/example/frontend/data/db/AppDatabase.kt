package com.example.frontend.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.frontend.data.model.DeletedItem
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.StoreLocation

/*
    * Abstract class that extends RoomDatabase and creates the database instance
 */
@Database(entities = [ShoppingItem::class, ShoppingList::class, StoreLocation::class, DeletedItem::class], version = 11, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun storeLocationDao(): StoreLocationDao
    abstract fun deletedItemDao(): DeletedItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shopping_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
