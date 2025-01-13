/**

Purpose: estimate the energy cost of your smart home. 
This app allows to monitor energy consumption costs for all powerMeasurement devices and account for changing energy rates over time.
It will also give a yearly cost estimate



Device Discovery:

Identify all devices on the Hubitat hub with the powerMeasurement capability.
Offer option to ignore some devices (e.g. a general power meter)
Option to select a Main power meater, which will be used to compare Smart Home's energy cost to overall cost. 
Option to enter the total kwh for the current month (from bill)

Data Parsing:

Retrieve the total energy consumption (kWh) and usage duration for each identified device.
User Input:

Provide the user with a form to input:
Supply Price (cost per kWh).
Delivery Price (cost per kWh).

Price Tracking:

Save user-provided prices into a map that maintains a record of prices by month.
Allow for updates to the prices, ensuring past data is retained.

Data Handling:

Calculate and organize data into a summary, including:
Device name.
Total kWh.
Usage duration.
Cost breakdown using the entered prices: 
- Per device: cost per month, cost per year (past year, if duration >= 365 days & current year (estimated))
- Total: per month, per year. Past years (if any, current year (estimated))
- If Applicable: total monthly and yearly cost (from devices) / total monthly and yearly cost (from Main Power Meter), ratio in %

final summary: "Smart Home Total Cost" in $ & ratio (if applicable)

*//** 
 * Last Updated: 2025-01-13
 */

