package ru.kbr.cargoshare

/** Local preview only, never used in release builds or as real availability. */
internal fun offlineDemoCatalog(): List<Vehicle> = listOf(
    Vehicle("gazelle-next", "ГАЗель NEXT", "Тентованный фургон", 1500, 16, 550, 3400,
        "Механика", "Нальчик · демонстрационная точка", "Демонстрационный автомобиль", "", null, null),
    Vehicle("ford-transit", "Ford Transit", "Цельнометаллический фургон", 1200, 12, 650, 3900,
        "Механика", "Нальчик · демонстрационная точка", "Демонстрационный автомобиль", "", null, null),
    Vehicle("largus-van", "LADA Largus фургон", "Компактный фургон", 650, 3, 350, 2200,
        "Механика", "Нальчик · демонстрационная точка", "Демонстрационный автомобиль", "", null, null),
)
