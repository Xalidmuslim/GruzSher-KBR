package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun fleetClass(v: Vehicle) = if(v.type.contains("тент",true)) "Тентованные" else "Цельнометаллические"

@Composable fun PremiumFleetScreen(cars: List<Vehicle>, loading: Boolean, onSelect:(Vehicle)->Unit, onRefresh:()->Unit,onLiveDemo:()->Unit, active:Boolean=false) {
    var mapMode by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("Все") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    val shown=cars.filter {filter=="Все" || fleetClass(it)==filter}
    val carousel=rememberLazyListState()
    val scroll=rememberLazyListState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(carousel.firstVisibleItemIndex,filter) { shown.getOrNull(carousel.firstVisibleItemIndex)?.let {selectedId=it.id} }
    LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().background(PremiumTheme.Paper),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { PremiumBrandHeader() }
        if(active) item {Button(onClick=onLiveDemo,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Outlined.PlayArrow,null);Text("Вернуться к поездке")}}
        item { PremiumDemoBanner(cars.firstOrNull(),onClick=onLiveDemo) }
        item { Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Найти грузовик",fontSize=23.sp,fontWeight=FontWeight.ExtraBold,color=PremiumTheme.Text,modifier=Modifier.weight(1f))
            IconButton(onClick=onRefresh,enabled=!loading,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.Refresh,"Обновить автопарк")}
            Surface(shape=RoundedCornerShape(30.dp),color=Color.White) {
                Row { TextButton(onClick={mapMode=true},colors=ButtonDefaults.textButtonColors(containerColor=if(mapMode) PremiumTheme.Green else Color.White,contentColor=if(mapMode) Color.White else PremiumTheme.Muted)) {Icon(Icons.Outlined.Map,null,Modifier.size(17.dp));Text(" На карте",fontSize=10.sp)}
                    TextButton(onClick={mapMode=false}) {Text("Список",fontSize=10.sp)} }
            }
        } }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf("Все","Тентованные","Цельнометаллические")) {name ->
                FilterChip(selected=filter==name,onClick={filter=name;scope.launch {carousel.scrollToItem(0)}},label={Text(name,fontSize=11.sp)},leadingIcon={Icon(if(name=="Все") Icons.Outlined.LocalShipping else Icons.Outlined.Inventory2,null,Modifier.size(18.dp))},shape=RoundedCornerShape(24.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=PremiumTheme.Green,selectedLabelColor=Color.White,selectedLeadingIconColor=Color.White,containerColor=Color.White))
            }
        } }
        if(loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(mapMode) item {
            Box(Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(20.dp)).background(PremiumTheme.Mint)) {
                NalchikMap(shown,{car ->selectedId=car.id;scope.launch {carousel.animateScrollToItem(shown.indexOf(car).coerceAtLeast(0))}},Modifier.fillMaxSize(), selectedVehicleId=selectedId)
                Surface(Modifier.align(Alignment.TopStart).padding(9.dp),shape=RoundedCornerShape(24.dp),color=Color.White) {Text("🚚  Грузовики · ${shown.size}",modifier=Modifier.padding(10.dp),fontSize=11.sp,color=PremiumTheme.GreenDark)}
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),shape=RoundedCornerShape(20.dp),color=Color.White) {Text("ⓘ Учебные точки — не GPS автомобилей",modifier=Modifier.padding(6.dp),fontSize=9.sp,color=PremiumTheme.GreenDark)}
            }
        }
        item {Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Доступны для аренды",fontSize=19.sp,fontWeight=FontWeight.Bold,color=PremiumTheme.Text,modifier=Modifier.weight(1f))
            TextButton(onClick={mapMode=!mapMode}) {Text(if(mapMode) "Смотреть все ›" else "На карту ›",fontSize=11.sp)}
        }}
        if(shown.isEmpty()) item { Text("В этой категории нет автомобилей. Измените фильтр.",modifier=Modifier.padding(20.dp)) }
        else if(mapMode) item {LazyRow(state=carousel,horizontalArrangement=Arrangement.spacedBy(9.dp)) {items(shown,key={it.id}) {car ->
            PremiumFleetCard(car,Modifier.width(225.dp),selectedId==car.id) {onSelect(car)}
        }}}
        else items(shown,key={it.id}) {car -> PremiumFleetCard(car,Modifier.fillMaxWidth(),false) {onSelect(car)} }
    }
}

@Composable private fun PremiumFleetCard(car:Vehicle,modifier:Modifier,selected:Boolean,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(1.dp),border=if(selected) androidx.compose.foundation.BorderStroke(2.dp,PremiumTheme.Green) else null) {
        Column(Modifier.padding(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Box {
                TruckArtwork(car,Modifier.fillMaxWidth().height(112.dp))
                Surface(Modifier.padding(4.dp),shape=RoundedCornerShape(18.dp),color=Color(0xFFDAFFE2)) {Text("● ДОСТУПЕН · ДЕМО",fontSize=9.sp,color=PremiumTheme.Green,modifier=Modifier.padding(horizontal=7.dp,vertical=3.dp))}
            }
            Text(car.name,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,color=PremiumTheme.Text,modifier=Modifier.padding(horizontal=3.dp))
            Text(car.type,fontSize=10.sp,color=PremiumTheme.Muted,modifier=Modifier.padding(horizontal=3.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2,null,Modifier.size(15.dp),tint=PremiumTheme.Muted)
                Text(" ${car.payloadKg} кг    ◇ ${car.cargoVolumeM3} м³",fontSize=10.sp,color=PremiumTheme.Muted)
            }
            Text("⌖ Нальчик · учебная точка",fontSize=10.sp,color=PremiumTheme.Muted)
            Row(Modifier.fillMaxWidth().padding(top=3.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("от ",fontSize=11.sp,color=PremiumTheme.GreenDark)
                Text("${car.hourlyPriceRub} ₽/ч",fontSize=19.sp,fontWeight=FontWeight.ExtraBold,color=PremiumTheme.GreenDark,modifier=Modifier.weight(1f))
                Surface(color=PremiumTheme.Green,shape=RoundedCornerShape(20.dp)) {Text("Выбрать →",color=Color.White,fontSize=11.sp,modifier=Modifier.padding(10.dp))}
            }
        }
    }
}
