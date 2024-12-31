/*
*  Copyright 2016 elfege
*
*    Eternal Sunshine©: Adjust dimmers with illuminance and (optional) motion
*
*    Software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
*    
*    The name Eternal Sunshine as an illuminance management software is protected under copyright
*
*  Author: Elfege
*/

definition(
    name: "APP TEST",
    namespace: "elfege",
    author: "elfege",
    description: "test",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
)

preferences {

    page name: "pageSetup"

}
def pageSetup() {


    def pageProperties = [
        name: "pageSetup",
        title: "${app.label}",
        nextPage: null,
        install: true,
        uninstall: true
    ]

    return dynamicPage(pageProperties) {

        section("Logging"){

            def now = now()

            input "enabledebug", "bool", title: "Debug logs", submitOnChange: true
            input "tracedebug", "bool", title: "Trace logs", submitOnChange: true
            input "logwarndebug", "bool", title: "Warning logs", submitOnChange: true
            input "description", "bool", title: "Description Text", submitOnChange: true

            input "test", "bool", title: "test", submitOnChange: true

            atomicState.EnableDebugTime = atomicState.EnableDebugTime == null ? now : atomicState.EnableDebugTime
            atomicState.enableDescriptionTime = atomicState.enableDescriptionTime == null ? now : atomicState.enableDescriptionTime
            atomicState.EnableWarningTime = atomicState.EnableWarningTime == null ? now : atomicState.EnableWarningTime
            atomicState.EnableTraceTime = atomicState.EnableTraceTime == null ? now : atomicState.EnableTraceTime

            // if (enabledebug) {
            def m = [
                "<br>test:$test",
                "<br>enabledebug: $enabledebug",
                "<br>tracedebug: $tracedebug",
                "<br>logwarndebug: $logwarndebug",
                "<br>description: $description",
                "<br>atomicState.EnableDebugTime = $atomicState.EnableDebugTime",
                "<br>atomicState.enableDescriptionTime = $atomicState.enableDescriptionTime",
                "<br>atomicState.EnableWarningTime = $atomicState.EnableWarningTime",
                "<br>atomicState.EnableTraceTime = $atomicState.EnableTraceTime",
            ]
            log.debug m.join()
            // }



            if (enabledebug) atomicState.EnableDebugTime = now
            if (description) atomicState.enableDescriptionTime = now
            if (logwarndebug) atomicState.EnableWarningTime = now
            if (tracedebug) atomicState.EnableTraceTime = now

            atomicState.lastCheckTimer = now // ensure it won't run check_logs_timer right away to give time for states to update

        }
    }
}
