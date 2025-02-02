/*
 * Solcast_dual
 *
 *
 *  Change History:
 *
 *      Date          Source        Version     What                                              
 *      ----          ------        -------     ----                                              
 *      2025-01-30    StarkTemplar  1.0.4       Update to allow only 1 resource ID to be used and still work
 *      2025-01-31    StarkTemplar  1.0.5       Updated html tile to show all 3 estimates.
 *      2025-02-01    StarkTemplar  1.0.6       Update to add test mode. This saves the API json response in a state variable to avoid API calls.
 *      2025-02-02    StarkTemplar  1.0.7       Updated Peak Metrics to not be cumulative.
 */

metadata {
    definition(
        name: "Solcast_dual",
        namespace: "StarkTemplar",
        author: "StarkTemplar",
        importUrl: "https://raw.githubusercontent.com/StarkTemplar/Solcast_dual/refs/heads/main/solcast.groovy",    
    ) {
        capability "Refresh"
        capability "EnergyMeter"
        capability "PowerMeter"

        attribute "energy", "number"
        attribute "power", "number"
        attribute "24 Hour Peak Production_a", "number"
        attribute "24 Hour Peak Production_b", "number"
        attribute "48 Hour Peak Production_a", "number"
        attribute "48 Hour Peak Production_b", "number"
        attribute "72 Hour Peak Production_a", "number"
        attribute "72 Hour Peak Production_b", "number"
        attribute "1 Hour Estimate_a", "number"
        attribute "1 Hour Estimate_b", "number"
        attribute "1 Hour Estimate", "number"
        attribute "one_hour_estimate", "number"
        attribute "24 Hour Estimate", "number"
        attribute "24 Hour Estimate - High", "number"
        attribute "24 Hour Estimate - Low", "number"
        attribute "48 Hour Estimate", "number"
        attribute "48 Hour Estimate - High", "number"
        attribute "48 Hour Estimate - Low", "number"
        attribute "72 Hour Estimate", "number"
        attribute "72 Hour Estimate - High", "number"
        attribute "72 Hour Estimate - Low", "number"
        attribute "lastUpdate", "string"
        attribute "html24hour", "string"
        attribute "html72hour", "string"
        attribute "jsonResponse", "string"
    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable Info logging", defaultValue: true, description: ""
        input name: "debugLog", type: "bool", title: "Enable Debug logging", defaultValue: false, description: ""
        input name: "htmlTile", type: "bool", title: "Create Dashboard Tile?", defaultValue: false, description: ""
        input name: "api_key", type: "string", title: "API Key", required: true
        input name: "resource_id_a", type: "string", title: "Site Resource ID_a", required: true
        input name: "resource_id_b", type: "string", title: "Site Resource ID_b", required: false
        input("refresh_interval", "enum", title: "How often to refresh the forecast data", options: [
            0: "Do NOT update",
            30: "30 minutes",
            1: "1 Hour",
            3: "3 Hours",
            8: "8 Hours",
            12: "12 Hours",
            24: "Daily",
        ], required: true, defaultValue: "0")
        input name: "refreshhour", type: "enum", title: "Refresh time (hour)", defaultValue: 0, description: "Only applies to Daily interval", options:["0","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23"]
        input name: "refreshminute", type: "enum", title: "Refresh time (minute)", defaultValue: 0, options:["1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31","32","33","34","35","36","37","38","39","40","41","42","43","44","45","46","47","48","49","50","51","52","53","54","55","56","57","58","59"]
        input name: "testMode", type: "bool", title: "Test Mode", defaultValue: false, description: "Reuse result from API to prevent too many API calls"

    }
}
import groovy.json.*;

def version() {
    return "1.0.7"
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
        def refreshseconds = 0
        if (settings.randomize) {
            refreshseconds = Math.abs(new Random().nextInt() % 59) +1
        }
        if (settings.refresh_interval == "24") { 
           schedule("${refreshseconds} ${settings.refreshminute} ${settings.refreshhour} ? * * *", refresh, [overwrite: true])
        } else if(settings.refresh_interval == "30"){
        schedule("${refreshseconds} */30 * ? * *", refresh, [overwrite: true])
        } else {
        schedule("${refreshseconds} ${refreshminute} */${settings.refresh_interval} ? * * *", refresh, [overwrite: true])
        }
    } else{
        unschedule(refresh)
    }
    state.version = version()
}

def refresh() {
     
    outputTZ = TimeZone.getTimeZone('UTC')
    def traceLog = true
    
    def next1_a = 0;
    def next24_a = 0;
    def next24High_a = 0;
    def next24Low_a = 0;
    def next48_a = 0;
    def next48High_a = 0;
    def next48Low_a = 0;
    def next72_a = 0;
    def next72High_a = 0;
    def next72Low_a = 0;
    
    host = "https://api.solcast.com.au/rooftop_sites/${resource_id_a}/forecasts?format=json&api_key=${api_key}&hours=72"
    if ( testMode == true ) {
        forecasts = new groovy.json.JsonSlurper().parseText(device.currentValue("jsonResponse"))
        log.info("testMode enabled, skipping API call and reusing JSON from previous call")
        if(traceLog) log.trace("forecasts: " + JsonOutput.toJson(forecasts))
    } else {
        forecasts = apiCall(host)
    }
    if ( forecasts != false ) {
        now = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        sendEvent(name: "lastUpdate", value: timeToday(now))
        //get local time for last update
        nowlocal = new Date().format("MM-dd HH:mm")

        def size = forecasts.size();
        if(traceLog) log.trace("size: ${size}")
        for(int x=0; x<size; x++){
            if(traceLog) log.trace x + " : " + forecasts[x]
            //pv estimates are divided by 2 because the data is listed in 30 minute intervals.
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
        forecast24_a = forecasts.findAll { it.period_end < tomorrow }
        if(traceLog) log.trace ("forecast24_a: ${forecast24_a}")
        peak24_a = forecast24_a.max() { it.pv_estimate }

        twoDays = new Date().plus(2).format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
        forecast48_a = forecasts.findAll { it.period_end < twoDays && it.period_end > tomorrow }
        if(traceLog) log.trace ("forecast48_a: ${forecast48_a}")
        peak48_a = forecast48_a.max() { it.pv_estimate }

        forecast72_a = forecasts.findAll { it.period_end > twoDays }
        if(traceLog) log.trace ("forecast72_a: ${forecast72_a}")
        peak72_a = forecast72_a.max() { it.pv_estimate }
        if(debugLog) log.debug  "{ \"next1_a\": " + next1_a + ", \"next24_a\": " +  next24_a + ", \"next24High_a\": " +  next24High_a + ", \"next24Low_a\": " + next24Low_a  + ", \"next48_a\": " + next48_a + ", \"next48High_a\": " + next48High_a + ", \"next48Low_a\": " + next48Low_a + ", \"next72_a\": " + next72_a + ", \"next72High_a\": " + next72High_a + ", \"next72Low_a\": " +  next72Low_a + "}";
    }
    
    //init B variables before resource ID check so the totalling function is not adding null values
    def next1_b = 0;
    def next24_b = 0;
    def next24High_b = 0;
    def next24Low_b = 0;
    def next48_b = 0;
    def next48High_b = 0;
    def next48Low_b = 0;
    def next72_b = 0;
    def next72High_b = 0;
    def next72Low_b = 0;
	
    //only make the 2nd API call if resource_id_b is valid
    if ( resource_id_b != null ) {    
        // API call for b site
        host = "https://api.solcast.com.au/rooftop_sites/${resource_id_b}/forecasts?format=json&api_key=${api_key}&hours=72"
        if ( testMode == true ) {
            forecasts = new groovy.json.JsonSlurper().parseText(device.currentValue("jsonResponse"))
            log.info("testMode enabled, skipping API call and reusing JSON from previous call")
            if(traceLog) log.trace("forecasts: " + JsonOutput.toJson(forecasts))
        } else {
            //delay between API calls
            pauseExecution(30000)
            forecasts = apiCall(host)
        }
        if ( forecasts != false ) {
            size = forecasts.size();
            for(int x=0; x<size; x++){
                if(traceLog) log.trace x + " : " + forecasts[x]
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
            if(traceLog) log.trace ("forecast24_b: ${forecast24_b}")
            peak24_b = forecast24_b.max() { it.pv_estimate }

            twoDays = new Date().plus(2).format("yyyy-MM-dd'T'HH:mm:ss'Z'",outputTZ)
            forecast48_b = forecasts.findAll { it.period_end < twoDays && it.period_end > tomorrow }
            if(traceLog) log.trace ("forecast48_b: ${forecast48_b}")
            peak48_b = forecast48_b.max() { it.pv_estimate }

            forecast72_b = forecasts.findAll { it.period_end > twoDays }
            if(traceLog) log.trace ("forecast72_b: ${forecast72_b}")
            peak72_b = forecast72_b.max() { it.pv_estimate }
        }
    } else {
        if(logEnable) log.info("Skipping 2nd API call because resouce ID B is null")
    }
    
    //log message for b totals
    if(debugLog) log.debug  "{ \"next1_b\": " + next1_b + ", \"next24_b\": " +  next24_b + ", \"next24High_b\": " +  next24High_b + ", \"next24Low_b\": " + next24Low_b  + ", \"next48_b\": " + next48_b + ", \"next48High_b\": " + next48High_b + ", \"next48Low_b\": " + next48Low_b + ", \"next72_b\": " + next72_b + ", \"next72High_b\": " + next72High_b + ", \"next72Low_b\": " +  next72Low_b + "}";    

    //calculate totals
    if ( forecasts != false ) {
        est_1hour = (next1_a + next1_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_24hour = (next24_a + next24_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_48hour = (next48_a + next48_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_72hour = (next72_a + next72_b).setScale(1, BigDecimal.ROUND_HALF_UP)
    
        est_24hour_high = (next24High_a + next24High_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_48hour_high = (next48High_a + next48High_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_72hour_high = (next72High_a + next72High_b).setScale(1, BigDecimal.ROUND_HALF_UP)
    
        est_24hour_low = (next24Low_a + next24Low_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_48hour_low = (next48Low_a + next48Low_b).setScale(1, BigDecimal.ROUND_HALF_UP)
        est_72hour_low = (next72Low_a + next72Low_b).setScale(1, BigDecimal.ROUND_HALF_UP)
    
        //round values if they are not integers
        if (peak24_a.pv_estimate instanceof Integer) {
            rnd_peak24_a = peak24_a.pv_estimate
        } else {
            rnd_peak24_a = peak24_a.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
        }
        if (peak48_a.pv_estimate instanceof Integer) {
            rnd_peak48_a = peak48_a.pv_estimate
        } else {
            rnd_peak48_a = peak48_a.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
        }
        if (peak72_a.pv_estimate instanceof Integer) {
            rnd_peak72_a = peak72_a.pv_estimate
        } else {
            rnd_peak72_a = peak72_a.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
        }
        if ((next1_a*1000) instanceof Integer) {
            rnd_next1_a = (next1_a*1000)
        } else {
            rnd_next1_a = (next1_a*1000).setScale(1, BigDecimal.ROUND_HALF_UP)
        }

        if ( peak24_b ) {
            if (peak24_b.pv_estimate instanceof Integer) {
                rnd_peak24_b = peak24_b.pv_estimate
            } else {
                rnd_peak24_b = peak24_b.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
            }
            if (peak48_b.pv_estimate instanceof Integer) {
                rnd_peak48_b = peak48_b.pv_estimate
            } else {
                rnd_peak48_b = peak48_b.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
            }
            if (peak72_b.pv_estimate instanceof Integer) {
                rnd_peak72_b = peak72_b.pv_estimate
            } else {
                rnd_peak72_b = peak72_b.pv_estimate.setScale(1, BigDecimal.ROUND_HALF_UP)
            }
            if ( (next1_b*1000) instanceof Integer) {
                rnd_next1_b = (next1_b*1000)
            } else {
                rnd_next1_b = (next1_b*1000).setScale(1, BigDecimal.ROUND_HALF_UP)
            }
        } else {
            rnd_peak24_b = 0
            rnd_peak48_b = 0
            rnd_peak72_b = 0
            rnd_next1_b = 0
        }
    
        //send events
        sendEvent(name: "24 Hour Peak Production_a", value: rnd_peak24_a)
        sendEvent(name: "24 Hour Peak Production_b", value: rnd_peak24_b)
        sendEvent(name: "48 Hour Peak Production_a", value: rnd_peak48_a)
        sendEvent(name: "48 Hour Peak Production_b", value: rnd_peak48_b)
        sendEvent(name: "72 Hour Peak Production_a", value: rnd_peak72_a)
        sendEvent(name: "72 Hour Peak Production_b", value: rnd_peak72_b)
        sendEvent(name: "1 Hour Estimate_a", value: rnd_next1_a)
        sendEvent(name: "1 Hour Estimate_b", value: rnd_next1_b)
        sendEvent(name: "1 Hour Estimate", value: est_1hour)
        sendEvent(name: "one_hour_estimate", value: est_1hour)
        sendEvent(name: "24 Hour Estimate", value: est_24hour)
        sendEvent(name: "energy", value: est_24hour)
        sendEvent(name: "power", value: est_24hour*1000)
        sendEvent(name: "24 Hour Estimate - High", value: est_24hour_high)
        sendEvent(name: "24 Hour Estimate - Low", value: est_24hour_low)
        sendEvent(name: "48 Hour Estimate", value: est_48hour)
        sendEvent(name: "48 Hour Estimate - High", value: est_48hour_high)
        sendEvent(name: "48 Hour Estimate - Low", value: est_48hour_low)
        sendEvent(name: "72 Hour Estimate", value: est_72hour)
        sendEvent(name: "72 Hour Estimate - High", value: est_72hour_high)
        sendEvent(name: "72 Hour Estimate - Low", value: est_72hour_low)
    }

    //log message for totals
    if(debugLog) log.debug  "{ \"next1\": " + est_1hour + ", \"next24\": " +  est_24hour + ", \"next24High\": " +  est_24hour_high + ", \"next24Low\": " + est_24hour_low  + ", \"next48\": " + est_48hour + ", \"next48High\": " + est_48hour_high + ", \"next48Low\": " + est_48hour_low + ", \"next72\": " + est_72hour + ", \"next72High\": " + est_72hour_high + ", \"next72Low\": " +  est_72hour_low + "}";    
    
    // Create Dashboard tile
    if(htmlTile && forecasts) {

        //24 html hour
        html24hour ="<div style='line-height:100%;'>24 Hour Estimates:<br></div>"
        html24hour +="<div style='line-height:100%;'>${est_24hour_low} / ${est_24hour} / ${est_24hour_high} kWh<br></div>"        
	    html24hour +="<div style='line-height:25%;'><br></div>"
	    html24hour +="<div style='line-height:100%;'><br>Peak Array Power:<br></div>"
        if ( resource_id_b != null ) {
            html24hour +="<div style='line-height:100%;'>${rnd_peak24_a} kW / ${rnd_peak24_b} kW</div>"
        } else {
            html24hour +="<div style='line-height:100%;'>${rnd_peak24_a} kW</div>"
        }
        html24hour +="<div style='line-height:25%;'><br></div>"
        if ( testMode == true ) {
            html24hour +="<div style='line-height:100%;' class='solarDate'><br>Updated: ${nowlocal} Test Mode</div>"
        } else {
            html24hour +="<div style='line-height:100%;' class='solarDate'><br>Updated: ${nowlocal}</div>"
        }
	
        sendEvent(name: "html24hour", value: html24hour)
        
        if(debugLog) log.debug ("html24hour:" + html24hour.length() + " ${html24hour}")
        if (html24hour.length() >= 1024) log.error ("html24hour exceeding 1024 charcters. Will not display on dashboard correctly.")
	
        //recalculate 48 and 72 hour values so they are not cumulative
        est_72hour_low = est_72hour_low - est_48hour_low
        est_72hour = est_72hour - est_48hour
        est_72hour_high = est_72hour_high - est_48hour_high

        est_48hour_low = est_48hour_low - est_24hour_low
        est_48hour = est_48hour - est_24hour
        est_48hour_high = est_48hour_high - est_24hour_high

        //72 html hour
	    html72hour ="<div style='line-height:100%;'>Estimates:<br></div>"
        html72hour +="<div style='line-height:100%;'>00-24hr ${est_24hour_low} / ${est_24hour} / ${est_24hour_high} kWh</div>"
        html72hour +="<div style='line-height:100%;'>24-48hr ${est_48hour_low} / ${est_48hour} / ${est_48hour_high} kWh</div>"   
        html72hour +="<div style='line-height:100%;'>48-72hr ${est_72hour_low} / ${est_72hour} / ${est_72hour_high} kWh</div>"   
	    html72hour +="<div style='line-height:15%;'><br></div>"
	    html72hour +="<div style='line-height:100%;'><br>Peak Array Power:<br></div>"
        if ( resource_id_b != null ) {
            html72hour +="<div style='line-height:100%;'>00-24hr ${rnd_peak24_a} kW / ${rnd_peak24_b} kW</div>"
            html72hour +="<div style='line-height:100%;'>24-48hr ${rnd_peak48_a} kW / ${rnd_peak48_b} kW</div>"
            html72hour +="<div style='line-height:100%;'>48-72hr ${rnd_peak72_a} kW / ${rnd_peak72_b} kW</div>"
        } else {
            html72hour +="<div style='line-height:100%;'>00-24hr ${rnd_peak24_a} kW</div>"
            html72hour +="<div style='line-height:100%;'>24-48hr ${rnd_peak48_a} kW</div>"
            html72hour +="<div style='line-height:100%;'>48-72hr ${rnd_peak72_a} kW</div>"
        }
        html72hour +="<div style='line-height:15%;'><br></div>"
        if ( testMode == true ) {
            html72hour +="<div style='line-height:100%;' class='solarDate'><br>Updated: ${nowlocal} Test Mode</div>"
        } else {
            html72hour +="<div style='line-height:100%;' class='solarDate'><br>Updated: ${nowlocal}</div>"
        }
	
        sendEvent(name: "html72hour", value: html72hour)

        if(debugLog) log.debug ("html72hour:" + html72hour.length() + " ${html72hour}")
        if (html72hour.length() >= 1024) log.error ("html72hour exceeding 1024 charcters. Will not display on dashboard correctly.")
	}
    
    if (settings.refresh_interval != "0" && settings.randomize) {
    //if randomize is selected, reset refresh time
        def refreshseconds = 0
        refreshseconds = Math.abs(new Random().nextInt() % 59) +1
    
        if (settings.refresh_interval == "24") { 
           schedule("${refreshseconds} ${settings.refreshminute} ${settings.refreshhour} ? * * *", refresh, [overwrite: true])
        } else if(settings.refresh_interval == "30"){
        schedule("${refreshseconds} */30 * ? * *", refresh, [overwrite: true])
        } else {
        schedule("${refreshseconds} ${refreshminute} */${settings.refresh_interval} ? * * *", refresh, [overwrite: true])
        }
    }       
}

def apiCall(host) {

    if(debugLog) log.debug host
    try {
        forecasts = httpGet([uri: host]) {resp -> def respData = resp.data.forecasts}
        if(debugLog) log.debug JsonOutput.toJson(forecasts)
        sendEvent(name: "jsonResponse", value: JsonOutput.toJson(forecasts))
        return forecasts
    }
    catch (groovyx.net.http.HttpResponseException exception) {
        if (debugLog) {
            log.debug exception
        }
        def httpError = exception.getStatusCode()
        if ( httpError == 429 ) {
            log.error("http 429 - rate limit error. You have sent too many API requests today.")
        } else {
            log.error("http error ${httpError}. Enable debugging for further info")
        }
        return false
    } 
    catch (exception) {
        if (debugLog) {
            log.debug exception
        }
        log.error("unknown error during API Call. Enable debugging for further info")
        return false
     }
}