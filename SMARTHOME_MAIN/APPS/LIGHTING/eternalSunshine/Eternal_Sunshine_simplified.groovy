definition(
    name: "Eternal Sunshine Simplified",
    namespace: "elfege",
    author: "elfege",
    description: "Adjust dimmers logarithmically based on illuminance",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w"
)

preferences {
    section("Select the dimmers you wish to control") {
        input "dimmers", "capability.switchLevel", title: "Dimmers", required: true, multiple: true
    }
    
    section("Select Illuminance Sensor") {
        input "sensor", "capability.illuminanceMeasurement", title: "Illuminance Sensor", required: true
    }
    
    section("Advanced Logarithmic Settings") {
        input "offset", "number", range: "3..10000", required: true, title: "Offset", description: "Value 'a' in logarithmic function"
        input "sensitivity", "decimal", range: "1.0..200.0", required: true, title: "Sensitivity", description: "Value 'b' (base) in logarithmic function"
        input "multiplier", "number", range: "3..3000", required: true, title: "Multiplier", description: "Value 'c' in logarithmic function"
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(sensor, "illuminance", illuminanceHandler)
}

def illuminanceHandler(evt) {
    def illuminance = evt.value as Integer
    def dimValue = calculateLogarithmicDimValue(illuminance)
    setDimmers(dimValue)
}

def calculateLogarithmicDimValue(illuminance) {
    def x = illuminance != 0 ? illuminance : 1
    def a = offset
    def b = sensitivity
    def c = multiplier
    
    def dimValue = (Math.log10(1 / x) / Math.log10(b)) * c + a
    dimValue = dimValue < 0 ? 0 : (dimValue > 100 ? 100 : dimValue.toInteger())
    
    return dimValue
}

def setDimmers(dimValue) {
    dimmers.setLevel(dimValue)
}