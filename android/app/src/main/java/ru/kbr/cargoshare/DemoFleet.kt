package ru.kbr.cargoshare

/** Local preview only, never used in release builds or as real availability. */
internal fun offlineDemoCatalog(): List<Vehicle> = listOf(
    Vehicle("gazelle-next", "ГАЗель NEXT", "Тентованный фургон", 1500, 16, 550, 3400,
        "Механика", "Нальчик · учебная точка №1 (север)", "Вымышленное местоположение — демо, не GPS автомобиля", "", 43.4945, 43.6071),
    Vehicle("ford-transit", "Ford Transit", "Цельнометаллический фургон", 1200, 12, 650, 3900,
        "Механика", "Нальчик · учебная точка №2 (юг)", "Вымышленное местоположение — демо, не GPS автомобиля", "", 43.4759, 43.6071),
    Vehicle("largus-van", "LADA Largus фургон", "Компактный фургон", 650, 3, 350, 2200,
        "Механика", "Нальчик · учебная точка №3 (восток)", "Вымышленное местоположение — демо, не GPS автомобиля", "", 43.4853, 43.6282),
)
