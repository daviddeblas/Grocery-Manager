package com.example.frontend.utils

import android.content.Context
import com.example.frontend.R

/**
 * Utility class to manage the translation of units
 */
class UnitHelper(private val context: Context) {

    companion object {
        const val UNIT_TYPE_UNIT = 0  // units, unités
        const val UNIT_TYPE_KG = 1    // kg
        const val UNIT_TYPE_LITER = 2 // L

        private val UNIT_NAMES = listOf("units", "unités")
    }

    fun normalizeUnit(unitString: String): Int {
        return when {
            UNIT_NAMES.contains(unitString.lowercase()) -> UNIT_TYPE_UNIT
            unitString.equals("kg", ignoreCase = true) -> UNIT_TYPE_KG
            unitString.equals("L", ignoreCase = true) ||
                    unitString.equals("l", ignoreCase = true) -> UNIT_TYPE_LITER
            else -> UNIT_TYPE_UNIT // By default
        }
    }

    fun getLocalizedUnitName(unitType: Int): String {
        val unitArray = context.resources.getStringArray(R.array.unit_types)
        return when (unitType) {
            UNIT_TYPE_KG -> "kg"
            UNIT_TYPE_LITER -> "L"
            else -> unitArray[0]
        }
    }

    fun translateUnit(unitString: String): String {
        val normalizedUnit = normalizeUnit(unitString)
        return getLocalizedUnitName(normalizedUnit)
    }

    fun getNextUnit(currentUnit: String): String {
        val unitArray = context.resources.getStringArray(R.array.unit_types)
        val normalizedCurrentUnit = normalizeUnit(currentUnit)

        val nextUnitType = (normalizedCurrentUnit + 1) % 3
        return getLocalizedUnitName(nextUnitType)
    }
}