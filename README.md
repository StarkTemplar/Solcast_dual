Solcast dual array Hubitat device driver

Forked from from https://github.com/youzer-name/Solcast_dual

Modified to allow one or two resource IDs to be entered. Collects data for each array and totals the site data.

Note: Each refresh by this driver makes up to 2 API calls to Solcast. Free Solcast accounts are very restrictive so do not make too many calls per day or you will get an HTTP 429 error. Test mode setting allows you to reuse the previous API result for testing purposes.


# Solcast - Hubitat
Hubitat Driver using the Solcast Solar Estimate API

1. Install the driver
2. Create a Virtual Device and apply the driver
3. Create a free account with [Solcast](https://toolkit.solcast.com.au/register/hobbyist)
4. Add your Site ID(s) and API key to the virtual device
5. Click Refresh to get your first estimate
6. Schedule the refresh to happen an hour or 2 before sunrise in your area for most the accurate results.
7. Add an attribute tile to your Hubitat dashboard for easy viewing (html24hour, html72hour).
8. CSS class solarDate is available to modify the date field in the tile.


Built using the [Solcast Solar API](https://docs.solcast.com.au/#forecasts-rooftop-site)

Gives an low, medium, high, and peak estimate for your solar array(s) production for the next 24, 48, and 72 hours.
