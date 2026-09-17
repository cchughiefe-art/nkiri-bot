package com.nkiridown.app

data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<SearchItem>
)
