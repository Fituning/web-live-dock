package com.plugin.webpreview.model

enum class ResponsivePreset(
    val id: String,
    val displayName: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val description: String
) {
    AUTO("auto", "Responsive (100%)", 0, 0, "Pleine largeur adaptative"),
    MOBILE("mobile", "Mobile (<sm · 390×844)", 390, 844, "iPhone / Smartphone standard"),
    SM("sm", "sm · Phablet (640×960)", 640, 960, "Tailwind sm: (640px)"),
    MD("md", "md · Tablette (768×1024)", 768, 1024, "iPad portrait / Tailwind md: (768px)"),
    LG("lg", "lg · Laptop (1024×768)", 1024, 768, "Laptop / Tailwind lg: (1024px)"),
    XL("xl", "xl · Desktop (1280×800)", 1280, 800, "Desktop / Tailwind xl: (1280px)"),
    TW_2XL("2xl", "2xl · Large (1536×960)", 1536, 960, "Grand écran / Tailwind 2xl: (1536px)");

    val isFixed: Boolean
        get() = targetWidth > 0 && targetHeight > 0

    companion object {
        fun fromId(id: String): ResponsivePreset {
            return values().firstOrNull { it.id == id } ?: AUTO
        }
    }
}
