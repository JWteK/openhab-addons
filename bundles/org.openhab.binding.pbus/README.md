## Pbus Binding

The Pbus binding integrates with Siemens PTM modules connected to a PTMoh interface (PTMod with adapted firmware) via an USB-RS422/RS485 module (based on a FTDI chipset) or a network connection (TCP/IP).

The binding exposes basic actions from the Pbus System that can be triggered from the smartphone/tablet interface, as defined.

Supported item types are switches, dimmers and rollershutters.
Pushbutton, sensors and input module states are retrieved and made available in the binding.

## Supported Things

In addition to the bridge modules mentioned in the section above, the supported Pbus modules are:

| CNT | 2C     | 0-65535 cumulative |                              |
|-----|--------|--------------------|------------------------------|
| DI  | 2D20   |                    |                              |
|     | 4D20   |                    |                              |
|     | 2D42   |                    |                              |
|     | 2D250  |                    |                              |
| AI  | 2R1K   | 0-4095             |                              |
|     | 2U10   | 0-8191             |                              |
|     | 2P100  | 0-8191             |                              |
|     | 2I25   | 0-8191             |                              |
|     | 2P1K   | 0-8191             |                              |
|     | 2I420  | 0-8191             |                              |
| DO  | 2Q250  |                    |                              |
|     | 2Q250M |                    | Feedback (Auto/Hand) + Value |
|     | 3QM3   | 0,1,2,3            | Feedback (Auto/Hand) + Value |
| AO  | 2Y10   | 0-240 = 0-100%     |                              |
|     | 2Y10M  | 0-240 = 0-100%     | Feedback (Auto/Hand)         |
|     | 2Y420  | 0-240 = 0-100%     |                              |

