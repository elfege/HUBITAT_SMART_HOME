# Hubitat Home Automation System - Project History

---

## November 7, 2025 - Zigbee Mesh Recovery and Hub Load Analysis

### Zigbee Mesh Complete Collapse on HUB 2 (Home 2 - C-8)

**Context:** All 63 Zigbee devices on HUB 2 (192.168.10.70) became unresponsive simultaneously.

#### Initial Diagnostics

**Zigbee Neighborhood Table Analysis:**

- Accessed via: `http://192.168.10.70/hub/zigbee/getChildAndRouteInfo`
- Initial state showed complete mesh failure:
  - No children connected
  - No neighbors visible
  - All routes showing "In Discovery" status
  - All devices routing "via [Unknown, 0000]"

#### Root Cause Identification

**Channel and Power Level Issues:**

- **Initial configuration:** Channel 20 (0x14), Power level 20
- **Problem:** Power level 20 was overdriving nearby devices
  - Signal too strong = corrupted packets
  - Increased interference with nearby radios
  - More collisions on the mesh

#### Resolution Steps

**Configuration Changes Applied:**

1. Reverted to Channel 20 (devices still listening on this channel)
2. Reduced power level from 20 to 12
3. Cold reboot of hub

**Recovery Timeline:**

- **T+5 minutes:** First 2 router devices reconnected
  - Light stairs (28C9) - LQI: 144
  - Light office dimmer (BCC7) - LQI: 252
- **T+10 minutes:** 13 router devices reconnected with excellent LQI values (140-255)
- **T+hours:** Battery devices gradually reconnecting as they report in

#### Technical Findings

**Power Level Impact:**

- Power level 20: Complete mesh collapse
- Power level 12: Stable mesh, optimal for indoor deployment
- **Lesson:** Higher power ≠ better performance in dense deployments

**C-8 Hub Characteristics:**

- Known sensitivity to high power levels
- Performs better at moderate power (12) than maximum (20)
- Zigbee radio stability issues documented across user community

### Hub Load Analysis and Performance Issues

#### CPU Load Warning Investigation

**Hub Status:**

- Load warning: "Hub load is severe"
- Actual CPU usage: 23.7% (Devices), 20.4% (Apps) = 44.1% total
- Hub considers this "severe" despite <50% utilization

#### Resource Usage Breakdown

**Top Device Consumers:**

- AC POWER OFFICE: 10.5% CPU
- Pressure Washer: 10.4% CPU
- Bedroom TV: 10.1% CPU
- Thermostats (3x): 15.8-16% CPU each (45% combined)
- Power monitoring devices: consistently high CPU usage

**App Instance Analysis:**

- 82 apps running simultaneously
- Multiple instances of "Motion V2" custom app:
  - Motion BOTH Terraces V2: 26% CPU
  - Motion Living V2: 9.6% CPU
  - Motion Office V2: 8.4% CPU
  - Motion Bathroom 5th Floor V2: 2.6% CPU

#### Platform Limitations Identified

**Hubitat Architecture Issues:**

- Warning threshold absurdly low (<50% CPU usage)
- Event subscription overhead multiplies per app instance
- Poor resource management for modest loads
- Platform struggles with loads trivial for comparable hardware

#### Custom Application Analysis

**Advanced Motion Lighting Management V2 (Groovy):**

- **Architecture:** OOP-based, modular design
- **Features implemented:**
  - Memoization for user override respect
  - Mode-specific dimming levels
  - Sensor failure handling
  - Button-based pause/resume with duration control
  - Illuminance awareness
  - Contact sensor integration
  - Watchdog functionality
  - Color temperature control
  - Auto-disabling debug logs (30min timer)

**Design Quality:**

- Significantly more sophisticated than Hubitat's built-in apps
- Proper state management and error handling
- However: Each instance adds overhead to platform

#### Migration Strategy Discussion

**Current 4-Hub Architecture:**

- **HUB 1 (C-7, 192.168.10.69):** Leak/fire/intrusion sensors (63 devices)
- **HUB 2 (C-8, 192.168.10.70):** Lighting/Zigbee/Z-Wave (63 Zigbee + Z-Wave devices)
- **HUB 3 (C-5):** Thermostats (6 devices)
- **HUB 4 (C-8):** API gateway via Hub Mesh

**Proposed Long-term Solution:**

- Migrate automation logic to Python on Dell server
- Use Hubitat Maker API for device control
- Eliminate hub CPU constraints entirely
- Maintain device connectivity while offloading processing

**Rationale:**

- Hubitat platform cannot handle sophisticated automation at scale
- Python provides proper programming capabilities
- Dell server resources trivialize current "severe" load
- Maintains local control while gaining flexibility

### Technical Specifications Referenced

#### Zigbee Configuration

- **Channel:** 20 (0x14) - 2.420 GHz
- **Power Level:** 12 (optimal for C-8 indoor deployment)
- **Mesh Type:** Router-based with mains-powered devices
- **Device Types:** Mix of battery (sensors) and mains (lights/switches)

#### Hub Hardware

- **C-8:** Arm Cortex-A53 (Raspberry Pi 3 equivalent)
- **C-7:** More stable Zigbee radio than C-8
- **C-5:** Proven reliability for Zigbee mesh

#### Network Configuration

- **Hub 1 IP:** 192.168.10.69
- **Hub 2 IP:** 192.168.10.70
- **Hub Mesh:** Active for cross-hub device sharing

### Lessons Learned

1. **Power Level Management:**
   - Start at moderate power (12) for dense deployments
   - High power can cause more problems than it solves
   - C-8 specifically sensitive to power level settings

2. **Mesh Recovery Process:**
   - Revert to last known-good channel (don't force migration during failure)
   - Reduce power level to prevent overdriving
   - Allow gradual recovery (mains devices first, battery devices follow)
   - Monitor via neighborhood table rather than device status alone

3. **Platform Limitations:**
   - Hubitat's "severe load" warnings are overly conservative
   - Multiple app instances cause multiplicative overhead
   - Built-in apps are resource-inefficient
   - Custom apps, while better, still constrained by platform

4. **Migration Considerations:**
   - Python + Maker API = viable escape path
   - Maintain 4-hub architecture for device diversity
   - Centralize complex logic externally
   - Use hubs purely as device controllers

### Outstanding Issues

**Unresolved:**

- C-8 Zigbee radio reliability concerns remain
- Platform CPU overhead for simple operations
- No hub-level CPU profiling tools available
- "Severe load" threshold cannot be configured

**Monitoring Required:**

- Zigbee mesh stability at power level 12
- Battery device reconnection completion (24-48 hour window)
- Hub CPU load trends with current configuration

---

## Project Context

**Total Device Count:** 200+ across 4 hubs
**Primary Challenges:** Platform scalability, Zigbee radio stability, resource management
**Current Focus:** Maintaining stability while planning long-term migration strategy
