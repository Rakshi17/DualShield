🛡️ Dual Shield: Edge AI-Based Smart Safety System
🚀 Overview
Dual Shield is an edge-based intelligent safety monitoring system designed for high-risk environments such as construction sites and industrial zones.
The system integrates wearable sensing, infrastructure monitoring, and real-time decision-making to predict and prevent accidents before they occur.
Unlike traditional systems, Dual Shield operates fully on edge devices, ensuring low latency, real-time response, and independence from cloud connectivity.

🎯 Key Features
👷 Wearable Safety Monitoring
Fall detection using IMU (MPU6050)
Body temperature monitoring (DS18B20)
Alcohol detection (MQ-3 sensor)
Vibration + buzzer alerts for immediate feedback
Chest-mounted ergonomic wearable design

🏗 Infrastructure Monitoring
Structural instability detection using IMU
Gas leakage detection (MQ series sensors)
Local decision-making using ESP32

🧠 Intelligent Safety Logic
Time-series analysis inspired by LSTM concepts
Sliding window-based risk detection
Threshold optimization inspired by metaheuristic approaches (HHO concept)
Real-time edge inference without cloud dependency

📱 Supervisor Dashboard
Live worker monitoring
Individual worker tracking
Attendance & shift management
Emergency alert system
Risk visualization

🧩 System Architecture
Wearable Node → Infrastructure Node → Mobile Dashboard
Wearable collects human data
Infrastructure evaluates environment + worker state
Mobile app visualizes and manages safety

🔌 Hardware Components
Wearable Node
ESP32
MPU6050 (Motion sensor)
DS18B20 (Temperature sensor)
MQ-3 (Alcohol sensor)
Coin vibration motor + buzzer
Battery system (18650 + TP4056 + MT3608 boost converter)

Infrastructure Node
ESP32
MPU6050 (Structural monitoring)
MQ gas sensor (MQ-4 / MQ-135)
Buzzer alert system

⚡ Power System
Rechargeable lithium battery (18650)
TP4056 charging module with protection
MT3608 boost converter for stable 5V output
Portable and fully wearable setup

🧠 Edge AI Concept
The system is designed using an edge AI approach:
Sensor data is analyzed in real-time using lightweight logic derived from time-series learning
Sliding window approach (last few seconds of data)
Predictive safety detection instead of reactive alerts
