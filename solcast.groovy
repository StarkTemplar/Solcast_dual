metadata {
    definition(
        name: "Solcast_dual",
        namespace: "alan_f",
        author: "Alan F",
        importUrl: "https://raw.githubusercontent.com/youzer-name/Solcast_dual/main/solcast.groovy",
    ) {
        capability "Refresh"
        capability "EnergyMeter"
        capability "PowerMeter"

        attribute "energy", "number"
        attribute "power", "number"
        attribute "1 Hour Estimate", "number"
        attribute "one_hour_estimate", "number"
        attribute "24 Hour Peak Production", "number"
        attribute "48 Hour Peak Production", "number"
        attribute "72 Hour Peak Production", "number"
        attribute "48 Hour Estimate", "number"
        attribute "72 Hour Estimate", "number"
        attribute "lastUpdate", "string"
    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable Info logging", defaultValue: true, description: ""
        input name: "debugLog", type: "bool", title: "Enable Debug logging", defaultValue: true, description: ""
        input name: "api_key", type: "string", title: "API Key", required: true
        input name: "resource_id_a", type: "string", title: "Site Resource ID_a", required: true
        input name: "resource_id_b", type: "string", title: "Site Resource ID_b", required: true
        input("refresh_interval", "enum", title: "How often to refresh the battery data", options: [
            0: "Do NOT update",
            30: "30 minutes",
            1: "1 Hour",
            3: "3 Hours",
            8: "8 Hours",
            12: "12 Hours",
            24: "Daily",
        ], required: true, defaultValue: "3")
    }
}

def version() {
    return "1.0.6"
}

def installed() {
    if (logEnable) log.info "Driver installed"

    state.version = version()
}

def uninstalled() {
    unschedule(refresh)
    if (logEnable) log.info "Driver uninstalled"
}

def updated() {
    if (logEnable) log.info "Settings updated"
    if (settings.refresh_interval != "0") {
        //refresh()
        if (settings.refresh_interval == "24") {
            schedule("51 7 4 ? * * *", refresh, [overwrite: true])
        } else if(settings.refresh_interval == "30"){
            schedule("51 */30 * ? * *", refresh, [overwrite: true])
        } else {
            schedule("51 7 */${settings.refresh_interval} ? * * *", refresh, [overwrite: true])
        }
    }else{
        unschedule(refresh)
    }
    state.version = version()
}

import groovy.json.JsonOutput;
def refresh() {
    outputTZ = TimeZone.getTimeZone('UTC')

    def next1 =0;
    def next24 = 0;
    def next24High = 0;
    def next24Low = 0;
    def next48 = 0;
    def next48High = 0;
    def next48Low = 0;
    def next72 = 0;
    def next72High = 0;
    def next72Low = 0;
    
    host = "https://api.solcast.com.au/rooftop_sites/${resource_id_a}/forecasts?format=json&api_key=${api_key}&hours=72"
    if(debugLog) log.debug host
    forecasts = httpGet([uri: host]) {resp -> def respData = resp.data.forecasts}
    //if(debugLog) log.debug JsonOutput.toJson(forecasts)
    def next1_a =0;
    def next24_a = 0;
    def next24High_a = 0;
    def next24Low_a = 0;
    def next48_a = 0;
    def next48High_a = 0;
    def next48Low_a = 0;
    def next72_a = 0;
    def next72High_a = 0;
    def next72Low_a = 0;
    def size = forecasts.size();
    for(int x=0; x<size; x++){
        if(debugLog) log.debug x + " : " + forecasts[x]
        pv_estimate = forecasts[x].pv_estimate/2
        pv_estimate_high = forecasts[x].pv_estimate90/2
        pv_estimate_low = forecasts[x].pv_estimate10/2
        if(x < 2){
            next1_a = next1_a + pv_estimate
        }
        if(x < 48){
            next24_a = next24_a + pv_estimate
            next24High_a = next24High_a + pv_estimate_high
            next24Low_a = next24Low_a + pv_estimate_low
        }
        if(x < 96){
            next48_a = next48_a + pv_estimate
            next48High_a = next48High_a + pv_estimate_high
            next48Low_a = next48Low_a + pv_estimate_low
        }
        next72_a = next72_a + pv_estimate
        next72High_a = next72High_a + pv_estimate_high
        next72Low_a = next72Low_a + pv_estimate_low
    }

    tomorrow = new Date().next().format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
    forecast24_a = forecasts.findAll { it.period_end < tomorrow}
    //if(debugLog) log.info forecast24_a
    peak24_a = forecast24_a.max() { it.pv_estimate }

    twoDays = new Date().plus(2).format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
    forecast48_a = forecasts.findAll { it.period_end < twoDays}
    peak48_a = forecast48_a.max() { it.pv_estimate }

    peak72_a = forecasts.max() { it.pv_estimate }
    
    if(logEnable) log.info  "{ \"next1_a\": " + next1_a + ", \"next24_a\": " +  next24_a + ", \"next24High_a\": " +  next24High_a + ", \"next24Low_a\": " + next24Low_a  + ", \"next48_a\": " + next48_a + ", \"next48High_a\": " + next48High_a + ", \"next48Low_a\": " + next48Low_a + ", \"next72_a\": " + next72_a + ", \"next72High_a\": " + next72High_a + ", \"next72Low_a\": " +  next72Low_a + "}";
    
// Duplicate API call
    host = "https://api.solcast.com.au/rooftop_sites/${resource_id_b}/forecasts?format=json&api_key=${api_key}&hours=72"
    if(debugLog) log.debug host
    forecasts = httpGet([uri: host]) {resp -> def respData = resp.data.forecasts}
    //if(debugLog) log.debug JsonOutput.toJson(forecasts)
    def next1_b =0;
    def next24_b = 0;
    def next24High_b = 0;
    def next24Low_b = 0;
    def next48_b = 0;
    def next48High_b = 0;
    def next48Low_b = 0;
    def next72_b = 0;
    def next72High_b = 0;
    def next72Low_b = 0;
    //def size = forecasts.size();
    size = forecasts.size();
    for(int x=0; x<size; x++){
        if(debugLog) log.debug x + " : " + forecasts[x]
        pv_estimate = forecasts[x].pv_estimate/2
        pv_estimate_high = forecasts[x].pv_estimate90/2
        pv_estimate_low = forecasts[x].pv_estimate10/2
        if(x < 2){
            next1_b = next1_b + pv_estimate
        }
        if(x < 48){
            next24_b = next24_b + pv_estimate
            next24High_b = next24High_b + pv_estimate_high
            next24Low_b = next24Low_b + pv_estimate_low
        }
        if(x < 96){
            next48_b = next48_b + pv_estimate
            next48High_b = next48High_b + pv_estimate_high
            next48Low_b = next48Low_b + pv_estimate_low
        }
        next72_b = next72_b + pv_estimate
        next72High_b = next72High_b + pv_estimate_high
        next72Low_b = next72Low_b + pv_estimate_low
    }

    tomorrow = new Date().next().format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
    forecast24_b = forecasts.findAll { it.period_end < tomorrow}
    //if(debugLog) log.info forecast24_b
    peak24_b = forecast24_b.max() { it.pv_estimate }

    twoDays = new Date().plus(2).format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
    forecast48_b = forecasts.findAll { it.period_end < twoDays}
    peak48_b = forecast48_b.max() { it.pv_estimate }

    peak72_b = forecasts.max() { it.pv_estimate }
//    if(logEnable) log.info  "next1_b " + next1_b + " - next24_b " +  next24_b + " - next24High_b " +  next24High_b + " - next24Low_b " + next24Low_b + " - next48_b " + next48_b + " - next48High_b " + next48High_b + " - next48Low_b " + next48Low_b + " - next72_b " + next72_b + " - next72High_b " + next72High_b + " - next72Low_b " +  next72Low_b;
    if(logEnable) log.info  "{ \"next1_b\": " + next1_b + ", \"next24_b\": " +  next24_b + ", \"next24High_b\": " +  next24High_b + ", \"next24Low_b\": " + next24Low_b  + ", \"next48_b\": " + next48_b + ", \"next48High_b\": " + next48High_b + ", \"next48Low_b\": " + next48Low_b + ", \"next72_b\": " + next72_b + ", \"next72High_b\": " + next72High_b + ", \"next72Low_b\": " +  next72Low_b + "}";    
    
//send events
    state.peak24_a = peak24_a.pv_estimate
    state.peak24_b = peak24_b.pv_estimate
    state.peak24 = peak24_a.pv_estimate + peak24_b.pv_estimate
    sendEvent(name: "24 Hour Peak Production", value: state.peak24)
    state.peak48_a = peak48_a.pv_estimate
    state.peak48_b = peak48_b.pv_estimate
    state.peak48 = peak48_a.pv_estimate + peak24_b.pv_estimate
    sendEvent(name: "48 Hour Peak Production", value: state.peak48)
    state.peak72_a = peak72_a.pv_estimate
    state.peak72_b = peak72_b.pv_estimate
    state.peak72 = peak72_a.pv_estimate + peak72_b.pv_estimate
    sendEvent(name: "72 Hour Peak Production", value: state.peak72)
    state.next1_a = next1_a*1000
    state.next1_b = next1_b*1000
    state.next1 = next1_a*1000 + next1_b*1000
    sendEvent(name: "1 Hour Estimate", value: next1_a*1000 + next1_b*1000)
    sendEvent(name: "one_hour_estimate", value: next1_a*1000 + next1_b*1000)
    state.next24 = next24_a + next24_b
    sendEvent(name: "energy", value: next24_a + next24_b)
    sendEvent(name: "power", value: next24_a*1000 + next24_b*1000)
    state.next24High = next24High_a + next24High_b
    state.next24Low = next24Low_a + next24Low_b
    state.next48 = next48_a + next48_b
    sendEvent(name: "48 Hour Estimate", value: next48_a + next48_b)
    state.next48High = next48High_a + next48High_b
    state.next48Low =next48Low_a + next48Low_b
    state.next72 = next72_a + next72_b
    sendEvent(name: "72 Hour Estimate", value: next72_a + next72_b)
    state.next72High = next72High_a + next72High_b
    state.next72Low = next72Low_a + next72Low_b


    now = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
    state.lastUpdate = timeToday(now)
    sendEvent(name: "lastUpdate", value: state.lastUpdate)
    
//    if(logEnable) log.info  "next1 " + state.next1 + " - next24 " +  state.next24 + " - next24High " +  state.next24High + " - next24Low " + state.next24Low + " - next48 " + state.next48 + " - next48High " + state.next48High + " - next48Low " + state.next48Low + " - next72 " + state.next72 + " - next72High " + state.next72High + " - next72Low " +  state.next72Low;
    if(logEnable) log.info  "{ \"next1\": " + state.next1 + ", \"next24\": " +  state.next24 + ", \"next24High\": " +  state.next24High + ", \"next24Low\": " + state.next24Low  + ", \"next48\": " + state.next48 + ", \"next48High\": " + state.next48High + ", \"next48Low\": " + state.next48Low + ", \"next72\": " + state.next72 + ", \"next72High\": " + state.next72High + ", \"next72Low\": " +  state.next72Low + "}";    
    
}
