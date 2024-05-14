Solcast Dual array Hubitat device driver

Original code from https://raw.githubusercontent.com/ke7lvb/Solcast/main/solcast.groovy

Modified to allow two resource IDs to be entered. Collects data for each array and totals the site data.

Note: Each refresh by this driver makes 2 API calls to Solcast.


# Solcast - Hubitat
Hubitat Driver using the Solcast Solar Estimate API

1. Install the driver
2. Create a Virtual Device and apply the driver
3. Create a free account with [Solcast](https://toolkit.solcast.com.au/register/hobbyist)
4. Add your Site ID and API key to the virtual device
5. Click Refresh to get your first estimate


Built using the [Solcast Solar API](https://docs.solcast.com.au/#forecasts-rooftop-site)

Gives an estimate for your solar array production for the next 24, 48, and 72 hours 
