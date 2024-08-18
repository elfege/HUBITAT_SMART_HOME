private Map getColorValue() {
    switch (colorPreset) {
        case "Soft White":
            return [colorTemperature: 2700, level: 100]
        case "Warm White":
            return [colorTemperature: 3000, level: 100]
        case "Cool White":
            return [colorTemperature: 4000, level: 100]
        case "Daylight":
            return [colorTemperature: 6500, level: 100]
        case "Red":
            return [hue: 0, saturation: 100, level: 100]
        case "Green":
            return [hue: 120, saturation: 100, level: 100]
        case "Blue":
            return [hue: 240, saturation: 100, level: 100]
        case "Yellow":
            return [hue: 60, saturation: 100, level: 100]
        case "Purple":
            return [hue: 280, saturation: 100, level: 100]
        case "Pink":
            return [hue: 300, saturation: 100, level: 100]
        case "Custom":
            return [colorTemperature: customColorTemperature, level: 100]
        default:
            return [colorTemperature: 6500, level: 100]  // Default to Daylight
    }
}