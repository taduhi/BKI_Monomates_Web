# Complete Development Plan: Plastique Recycling Reward Platform

## 1. Product Identity

**Product Name**
Plastique Recycling Reward Platform

**Product Vision**
Recycle plastic at smart-bin locations and earn rewards through verified deposit actions.

**Core User Promise**
Users can find a nearby smart bin, check what item is accepted, scan the QR code, insert an
accepted plastic product, earn Plastique Tokens, and redeem rewards.

**Product Positioning**
This is a recycling reward platform with:
- Smart-bin discovery
- QR-based user session
- Low-cost hardware deposit detection
- Camera-assisted accepted-plastic product recognition
- Token reward system
- Voucher redemption
- Admin management

**Main Product Sentence**
Find a smart bin, insert an accepted clear plastic product, earn Plastique Tokens, and redeem
rewards.

## 2. Core Product Principle
The platform should not reward users for QR scanning alone.
The final logic is:
- Smart-bin discovery
- QR scan starts a deposit session on the web
- Hardware detects a physical deposit event using ultrasonic sensor
- The camera estimates whether the item is an accepted clear product.
- Backend links the hardware event to the active session.
- Tokens are awarded only after a valid deposit decision.
Therefore:
QR only = 0 token
Object without QR session = 0 token
QR + invalid object = 0 token
QR + valid deposit event = +1 Plastique Token (Only once a day for each available bin and
bonus when having accepted clear plastic product)
QR + accepted clear plastic product = +2 Plastique Tokens total (Currently, each valid
session only allowing one accepted plastic product)

## 3. Current Build Target

**Prototype v0 Includes**
Login/signup
Static bin list
Bin detail page
QR URL opens scan page
QR scan creates DepositSession
Manual simulated valid deposit event
Token reward logic
Token balance
Voucher redemption
Activity history
Admin: bins, vouchers, transactions

**Prototype v0 Does Not Include**
Real IR sensor integration
ESP32-CAM image upload
Camera classification
Load cell validation
Map view
Dynamic QR
Advanced fraud scoring
Voucher code pool
Partner cashier validation

## 4. Prototype Stages

### 4.1 Prototype v0: Web-Only Demo
Purpose:
Prove the software loop before real hardware integration.
Features:
User logs in
User sees static bin list
User opens bin detail
User scans QR URL
Backend creates DepositSession
Manual simulated deposit event is triggered
Backend decides reward
TokenTransactions are created
User sees token balance and activity
User redeems shared-code voucher
Admin manages bins, vouchers, and transactions
No real map and no real hardware are required in v0.

### 4.2 Prototype v1: IR Sensor Deposit Demo
Purpose:
Prove that a physical deposit can be detected during an active session.
Hardware:
Static QR code
Ultrasonic sensor
Reward logic:
QR session + valid IR deposit event = +1 plastique token (bonus if the deposit object is
accepted)
Important wording: Deposit detected (not meaning the object is accepted).

### 4.3 Prototype v2: Camera plastic product Detection Demo
Purpose:
Estimate whether the inserted item is an accepted clear plastic product.
Hardware:
Static QR code
Ultrasonic sensor
ESP32-CAM
Reward logic:
Valid deposit event = +1 Plastique Token
Accepted clear plastic product detected = +1 extra Plastique Token
Maximum reward:
2 Plastique Tokens per valid accepted-plastic product deposit, (+2 only once a day for each
bin, other accepted product only earn +1 in that day)
Camera classification should run on the backend or a connected laptop/server, not directly on
ESP32-CAM.

## 5. Demo Mode Assumptions
Prototype v0 uses simulated item detection.
Prototype v1 uses IR sensor detection for object insertion.
Prototype v2 uses camera classification under controlled lighting and fixed camera position.
Voucher codes are demo codes, not real payment vouchers.
Bin locations are manually entered.
Bin capacity may be manually updated by the admin.
Static QR codes are used for prototype simplicity.
The prototype recognizes plastic product appearance, not chemical material.

## 6. Token System

### 6.1 One Currency Only
Use only one visible currency:
Instead, use one currency with different earning reasons:
+1 Plastique Token for accepted clear plastic product detection
+1 Plastique Token for valid deposit for the first time in one bin a day

### 6.2 Final Reward Summary
QR only = 0 token
Object without QR session = 0 token
QR + invalid object = 0 token
QR + valid deposit event = +1 Plastique Token (Only once a day for each available bin and
bonus when having accepted clear plastic product)
QR + accepted clear plastic product = +2 Plastique Tokens total (Currently, each valid
session only allowing one accepted plastic product)

### 6.4 Valid Deposit Event
The platform should not reward “any item detected” blindly.
A valid deposit event depends on the prototype stage.

**Prototype v0**
QR session is active
+ item detection is manually simulated
+ system accepts this as a demo deposit

**Prototype v1**
QR session is active
+ IR sensor detects object insertion within 60 seconds
+ bin is in demo mode
+ event is not manually rejected

**Prototype v2**
QR session is active
+ IR sensor detects object insertion within 60 seconds
+ camera result is accepted_clear_plastic product, uncertain, or camera_failed with
acceptable IR evidence, displaying on the Web not on the smart bin because there is no screen
on the smartbin
Clearly rejected objects should not earn tokens.

### 6.5 Final Reward Cases

**Case 1: User Scans QR but Inserts Nothing**
Result:
No token awarded.
Reason:
QR check-in alone is not enough. The system must detect a valid deposit event.
User message:
No item was detected within 60 seconds. No token was awarded.

**Case 2: Plastic product Is Inserted Without QR Check-In**
Result:
No token awarded but the detection is still working
Reason:
The system cannot link the plastic product to a user but we can still collect that bottle if it is
available
System action:
Store hardware events as unlinked, but do not create a token transaction.

**Case 3: User Scans QR and Makes a Valid Deposit**
Result:
+1 Plastique Token (Bonus for the first deposit per day per account, only accepted if the
product is accepted)
Meaning:
The user started a session and the bin confirmed a valid deposit event.

**Case 4: User Scans QR and Inserts an Accepted Clear Plastic Product**
Result:
+1 Plastique Token for valid deposit (Bonus for the first deposit per day per person)
+1 Plastique Token for accepted clear plastic product detection
Maximum reward:
2 Plastique Tokens per valid accepted-plastic product deposit for the first time of the day per
account, the other

## 7. Daily Limit and Cooldown

### 7.1 Real Product Rule
For a real product:
Same user + same bin: max 2 rewarded deposits
Global cap: 10–20 tokens per day
Suspicious behavior: mark for review (over 40 token earn a day should have a recap)
The daily cap should count all earned Plastique Tokens, including extra tokens.

### 7.2 Daily Limit Timing
Check the daily limit twice.

**During QR Scan**
Check for user experience:
Has this user already reached the demo reward limit for this bin today?
If yes:
Create DepositSession without bonus for the check-in

## 8. Low-Cost Hardware Plan

### 8.1 Hardware
For the cheapest working prototype:
QR code
IR break-beam sensor
ESP32 or ESP32-CAM
LED/buzzer
This setup can prove:
User scanned QR
Something was inserted
The event happened during an active session

### 8.3 Hardware Roles

**QR Code**
Purpose:
Identify the bin
Start a user-linked DepositSession
The QR code alone does not give tokens.

**Ultra Sonic Sensor**
Purpose:
Detect when an object is in the bin
Trigger deposit event
Trigger camera capture

**ESP32-CAM**
Purpose:
Capture image of inserted item
Send image to backend
Support plastic product-appearance recognition
Connect hardware to Wi-Fi
Important:
ESP32-CAM is mainly used for image capture.
Classification should run on the backend or a connected laptop/server for easier prototyping.

## 9. Hardware-to-Backend Architecture
The hardware does not need to know the deposit session ID.
Use this architecture:
1. User scans QR on web app.
2. Backend creates active DepositSession for that bin.
3. Hardware detects object and sends bin-level event to backend.
4. Backend finds the active DepositSession for that bin.
5. Backend links hardware event to the session.
6. Backend evaluates reward.
7. Frontend polls session status and shows result.

## 10. One Active Session Per Bin
For the prototype:
One bin can only have one DepositSession with status = waiting_for_item.
If another user scans while the bin is busy:
This bin is currently processing another deposit. Please wait a moment.
This rule makes hardware-event matching safe because the backend can find the active
session by binId.

## 11. Static QR Limitation
For prototype simplicity:
Static QR codes are acceptable.
But state the limitation clearly:
Static QR codes can be copied or shared, so production should use dynamic QR codes,
location checks, or stronger hardware-linked validation.
Important:
Even with static QR, the system does not award tokens unless a hardware event occurs at the
matching bin during the active session window.

## 12. DepositSession State Machine
Use the simple state machine for development.

**Status Enum**
{waiting_for_item, rewarded, expired, rejected}
Remove: detected
Reason: detected adds complexity and is not necessary for the first implementation.

**State Transitions**
QR scan successful → waiting_for_item
waiting_for_item + valid deposit event → rewarded
waiting_for_item + no event before expiresAt → expired
waiting_for_item + invalid event → rejected
waiting_for_item + daily limit reached → rejected
waiting_for_item + bin unavailable → rejected
Final statuses are: {rewarded, expired, rejected}

## 13. Deposit Session Flow

**Step 1: User Scans QR**
The web app sends QR scan to backend.
Backend creates: DepositSession status = waiting_for_item
Session duration: 60 seconds
User message: QR scanned. Please insert one empty clear plastic product within 60 seconds.

**Step 2: Hardware Waits for Item**
The ultrasonic sensor detects whether an object is in the bin.
If no object is detected within 60 seconds: {Session expires, No token awarded}

**Step 3: Camera Captures Object**
ESP32-CAM captures the inserted item.
Classification should be done by backend or a connected laptop/server.
Possible camera statuses:
accepted_clear_plastic product
uncertain
not_accepted
camera_failed

**Step 4: Backend Awards Tokens**
Backend combines:
Active deposit session + valid hardware event + camera status + daily limit check
Then it decides:
No reward
Valid deposit reward
Accepted plastic product reward

## 14. Hardware Event Format
For prototype development, use one combined event instead of multiple raw sensor events.

**Event Type**
deposit_result

**Hardware Event Payload**
{
"eventId": "EVT_123",
"eventType": "deposit_result",
"binId": "BIN_001",
"irDetected": true,
"cameraStatus": "accepted_clear_plastic product",
"cameraPredictedClass": "accepted_clear_plastic product",
"cameraConfidence": 0.82,
"imageUrl": "uploads/session_123.jpg",
"timestamp": "2026-07-10T14:30:00Z"
}
For Prototype v0, this event can be simulated manually from the backend/admin/dev tool.
For Prototype v1, the event comes from the IR sensor.
For Prototype v2, the event includes camera status.

## 15. Hardware Event API
Endpoint:
POST /api/iot/deposit-events
Backend logic:
Find active DepositSession where:
- binId matches
- status = waiting_for_item
- expiresAt > current time
If found:
- link event to that session
- evaluate reward
If not found:
- store event as unlinked
- award no token
Security:
IoT endpoint must require deviceToken or API key.
Reject events from unknown devices.

## 16. Hardware Authentication
Hardware must authenticate when sending events.

**Header Format**
Authorization: Bearer DEVICE_TOKEN_123

**Devices Model**
id
deviceId
deviceTokenHash
binId
status: active/inactive
lastSeenAt
createdAt
updatedAt

**Security Rule**
The backend must check:
1. Device token is valid.
2. Device status is active.
3. Device is allowed to send events for the given binId.
A device registered to Bin A must not be able to send events for Bin B.

## 17. Camera Classification Pipeline
Camera classification is not required in Prototype v0 or v1.
For Prototype v2, use this flow:
1. ESP32-CAM captures image.
2. ESP32-CAM sends image to backend or local laptop server.
3. Backend stores image or temporarily processes it.
4. Backend sends image to classifier.
5. Classifier returns cameraStatus and confidence.
6. Backend stores result in HardwareEvents.
7. Backend decides reward.
Recommended development choice:
ESP32-CAM only captures image.
Classification runs on backend or local laptop server.

**Classifier Response Format**
{
"cameraStatus": "accepted_clear_plastic product",
"predictedClass": "accepted_clear_plastic product",
"confidence": 0.82
}

**Camera Status Enum**
accepted_clear_plastic product
uncertain
not_accepted
camera_failed

## 18. Camera Confidence Policy
Use four statuses:
accepted_clear_plastic product
uncertain
not_accepted
camera_failed
Confidence bands:
confidence >= 0.80
→ accepted_clear_plastic product
0.50 <= confidence < 0.80
→ uncertain
confidence < 0.50
→ not_accepted
camera/upload/model error
→ camera_failed
Reward decision:
accepted_clear_plastic product
→ valid deposit + extra token
→ 2 Plastique Tokens total
uncertain
→ valid deposit only
→ 1 Plastique Token
not_accepted
→ invalid deposit
→ 0 token
camera_failed
→ valid deposit only if IR/weight evidence is acceptable
→ 1 Plastique Token
User message for camera failure:
Deposit detected. Camera check was unavailable, so no extra token was awarded.

## 19. Session Expiry Handling
Each deposit session lasts 60 seconds.
Prototype approach:
DepositSessions can be expired lazily.
The backend checks expiry when:
A hardware event arrives
The frontend requests session status
The user refreshes the scan page
If current time is greater than expiresAt, update the session:
status = expired
rewardDecision = no_reward
tokensAwarded = 0
decisionReason = session_expired
A scheduled cleanup job can be added later, but it is not required for the first prototype.

## 20. Frontend Polling for Session Status
After QR scan, the frontend needs to know when the hardware event is processed.
Use polling for the prototype.
Endpoint:
GET /api/deposit-sessions/:id/status
Frontend behavior:
After QR scan, poll session status every 1–2 seconds.
Stop polling when status is rewarded, rejected, or expired.
Example response:
{
"status": "rewarded",
"tokensEarned": 2,
"message": "Accepted clear plastic product detected. You earned 2 Plastique Tokens total."
}
Waiting response:
{
"status": "waiting_for_item",
"tokensEarned": 0,
"message": "Please insert one empty clear plastic product within 60 seconds."
}
Expired response:
{
"status": "expired",
"tokensEarned": 0,
"message": "No item was detected within 60 seconds. No token was awarded."
}

## 21. Duplicate Event Protection
The backend must prevent duplicate token awards.
Rules:
Each DepositSession can be rewarded only once.
Before creating token transactions, backend checks:
if session.status == rewarded:
do not award again
Hardware events must include:
eventId
The backend stores processed event IDs. If the same event ID arrives again, ignore it.
This prevents duplicate rewards caused by:
IR sensor triggering twice
Network retries
Repeated hardware requests
Late camera result
Duplicate backend processing

**Correct Processing Order**
1. Authenticate device token.
2. Validate event payload.
3. Check eventId uniqueness.
4. Store HardwareEvent.
5. Find active DepositSession.
6. Process reward decision.
7. Mark eventId as processed.
Unique index:
HardwareEvents.eventId unique
or:
ProcessedHardwareEvents.eventId unique

## 22. Reward Decision Function
Use this exact decision logic.
decideReward(session, event, prototypeMode):
if session does not exist:
return no_reward, 0, "no_active_session"
if session.status is not waiting_for_item:
return no_reward, 0, "session_not_active"
if currentTime > session.expiresAt:
return no_reward, 0, "session_expired"
if event.irDetected is false:
return no_reward, 0, "no_deposit_detected"
if user already reached daily limit for this bin:
return no_reward, 0, "daily_bin_limit_reached"
if prototypeMode == "v0_manual":
return valid_deposit, 1, "manual_demo_deposit"
if prototypeMode == "v1_ir_only":
return valid_deposit, 1, "ir_deposit_detected"
if prototypeMode == "v2_camera":
if event.cameraStatus == "accepted_clear_plastic product":
return accepted_plastic product, 2, "accepted_clear_plastic product"
if event.cameraStatus == "uncertain":
return valid_deposit, 1, "camera_uncertain"
if event.cameraStatus == "camera_failed":
return valid_deposit, 1, "camera_failed_but_ir_valid"
if event.cameraStatus == "not_accepted":
return no_reward, 0, "not_accepted"
if prototypeMode == "v3_weight_camera":
if weightDelta is clearly abnormal:
return no_reward, 0, "abnormal_weight"
if event.cameraStatus == "accepted_clear_plastic product":
return accepted_plastic product, 2, "accepted_clear_plastic product"
if event.cameraStatus == "uncertain":
return valid_deposit, 1, "camera_uncertain_weight_valid"
if event.cameraStatus == "camera_failed":
return valid_deposit, 1, "camera_failed_weight_valid"
if event.cameraStatus == "not_accepted":
return no_reward, 0, "not_accepted"
return no_reward, 0, "unknown_rule"

## 23. Atomic Reward Creation
Reward processing must be atomic.
When a valid reward is decided, these operations should happen together:
1. Confirm DepositSession is still waiting_for_item.
2. Confirm eventId has not been processed.
3. Create TokenTransactions.
4. Update DepositSession with rewardDecision, tokensAwarded, decisionReason, and
transaction IDs.
5. Update tokenBalanceCached.
6. Mark eventId as processed.
Recommended:
Use MongoDB transaction session if available.
At minimum:
Use a unique index to prevent more than one reward decision per depositSessionId.
Use a unique index on HardwareEvents.eventId or ProcessedHardwareEvents.eventId.

## 24. Token Amount Sign Convention
Use signed amounts.
Earn transactions = positive amount
Spend transactions = negative amount
Admin adjustment = positive or negative amount
Examples:
{
"transactionType": "earn",
"amount": 1,
"tokenReason": "valid_deposit"
}
{
"transactionType": "spend",
"amount": -20,
"tokenReason": "voucher_redemption"
}
Balance formula:
tokenBalance = sum(amount where verificationStatus = confirmed)

## 25. Token Balance Rule
Token balance should be calculated from confirmed token transactions.

**Balance Includes**
Confirmed earning transactions
Confirmed spending transactions
Confirmed admin adjustments

**Balance Excludes**
Pending transactions
Rejected transactions
Expired sessions
Unlinked hardware events

**Cached Balance**
tokenBalanceCached can be stored for fast display.
But:
Periodically recalculate tokenBalanceCached from confirmed TokenTransactions.

## 26. QR Scan Method
For the first web prototype, do not build an in-app QR scanner.
Use a QR code that contains a URL:
https://app.com/scan?qrCodeId=BIN_QR_123
User flow:
1. User scans QR using phone camera.
2. Browser opens /scan?qrCodeId=BIN_QR_123.
3. If user is logged in, frontend calls POST /api/recycling/scan.
4. If user is logged out, redirect to login.
5. After login, resume scan using qrCodeId from URL.
This is easier than implementing browser camera QR scanning.
Browser QR scanning can be added later.

## 27. Logged-Out QR Flow
If user scans QR while logged out:
1. Keep qrCodeId in URL.
2. Redirect user to login.
3. After successful login, redirect back to /scan?qrCodeId=BIN_QR_123.
4. Frontend calls POST /api/recycling/scan.
5. Backend creates DepositSession.
Important:
Do not create DepositSession before login.
Reason:
The session must be linked to a user.

## 28. Bin Status Rules
TrashBins status enum:
available
full
maintenance
inactive
QR scan behavior:
available
→ allow DepositSession
full
→ block DepositSession
maintenance
→ block DepositSession
inactive
→ block DepositSession
User messages:
This bin is currently full. Please use another available bin.
This bin is under maintenance. Please use another available bin.
This bin is currently inactive. Please use another available bin.

## 29. User-Facing Messages

**QR Scan Success**
QR scanned. Please insert one empty clear plastic product within 60 seconds.

**No Item Inserted**
No item was detected within 60 seconds. No token was awarded.

**Daily Bin Limit Reached**
You already reached the reward limit for this bin today. Please use another available bin or
come back tomorrow.

**Valid Deposit Detected**
Deposit detected. You earned 1 Plastique Token.

**Accepted Clear plastic product Detected**
Accepted clear plastic product detected. You earned 2 Plastique Tokens total.

**Uncertain Camera Result**
Deposit detected, but we could not confidently confirm it as an accepted clear plastic product.
You earned 1 Plastique Token.

**Camera Failed**
Deposit detected. Camera check was unavailable, so no extra token was awarded.

**Not Accepted**
We could not confirm this as a valid deposit. No token was awarded.

## 30. Main User Pages

### 30.1 Login / Signup Page
Features:
Signup with name, email, password
Login with email and password
Password hashing
JWT authentication
Basic error messages

### 30.2 Bin List / Map Page
For v0, a static bin list is enough.
For v1 or later, add map.
First screen should show only:
Search / use my location
Nearest bin or bin list
Token balance if logged in
Avoid making this page a crowded dashboard.

**Bin Card Should Show**
Bin name
Distance
Status
Accepted item for extra token
Capacity
View details
Directions
Example:
KMITL Smart Bin A
350 m away
Available
Extra token: empty clear plastic products only
View details

### 30.3 Bin Detail Page
This is where the guide should be embedded.
Content:
Bin name
Address
Distance
Status
Capacity
Last updated time
Accepted extra-token item
Rejected examples
Preparation instructions
QR scan button
Directions button
Report problem button
Suggested text:
This bin gives extra tokens for empty clear plastic plastic products only.
How to earn:
1. Scan the QR code.
2. Insert one item within 60 seconds.
3. Earn 1 Plastique Token if the deposit is valid.
4. Earn 1 extra Plastique Token if the item is recognized as an accepted clear plastic product.
Preparation instructions:
Before inserting:
- Empty the liquid.
- Remove food residue.
- Insert only one plastic product at a time.
- Keep the plastic product visible to the camera.
Not accepted for extra token:
Plastic bags
Food-contaminated plastic
Foam boxes
Paper cups
Metal cans
Glass plastic products
Mixed-material packaging
Opaque containers

### 30.4 Scan / Deposit Session Page
This page appears after QR scan.

**State 1: Waiting for Item**
QR scanned. Please insert one empty clear plastic product within 60 seconds.

**State 2: Deposit Detected**
Deposit detected. Checking whether it is an accepted clear plastic product...

**State 3: Full Reward**
Accepted clear plastic product detected. You earned 2 Plastique Tokens total.

**State 4: Partial Reward**
Deposit detected. You earned 1 Plastique Token. No extra token was awarded.

**State 5: No Reward**
No item was detected within 60 seconds. No token was awarded.

### 30.5 Rewards Page
Purpose:
Allow users to redeem vouchers.
Features:
Show current token balance
Show vouchers user can afford
Show all vouchers
Show required token amount
Show expiry date
Show quantity left
Show redemption instructions
Confirm before redemption
Voucher confirmation modal:
Current balance: 25 Plastique Tokens
Voucher cost: 20 Plastique Tokens
Remaining balance after redemption: 5 Plastique Tokens
Expiry date: 30 August 2026
Redemption condition: Show this code at the partner store.
Buttons:
Cancel
Confirm redemption

### 30.6 Activity Page
Purpose:
Show user history.
Features:
Current token balance
Recent deposits
Token transaction history
Redeemed vouchers
Activity should group token transactions by depositSessionId.
User-facing activity card:
+2 Plastique Tokens
Accepted clear plastic product detected
KMITL Smart Bin A
10 July 2026, 14:30
Detail breakdown:
+1 token for valid deposit
+1 extra token for accepted clear plastic product
If only a valid deposit is confirmed:
+1 Plastique Token
Valid deposit detected
KMITL Smart Bin A
10 July 2026, 14:30
Voucher redemption example:
-20 Plastique Tokens
Redeemed Coffee Voucher
10 July 2026, 15:00

### 30.7 Profile Page
Keep simple in v1.
Features:
Name
Email
Logout
Basic account settings
Delay:
Privacy settings
Data export
Account deletion
unless required by course or project rules.

## 31. Admin Scope
For the first demo, admin should only include:
Bins
Vouchers
Transactions
Delay:
View users page
Bin reports page
Admin audit log UI
Advanced overview dashboard
The database can still include models for future support, but the UI does not need every
admin screen immediately.

## 32. Admin Pages

### 32.1 Manage Bins
Admins can:
Add bin
Edit bin
Disable bin
Update status
Update capacity
View QR code ID
Set accepted extra-token item
Fields:
Bin name
Address
Latitude
Longitude
Status
Capacity percentage
Accepted extra-token item
QR code ID
Last updated time

### 32.2 Manage Vouchers
Admins can:
Add voucher
Edit voucher
Disable voucher
Set token cost
Set expiry date
Set quantity
Set redemption instructions
For early prototype, use shared demo voucher codes.
Delay full voucher code pool until later.

### 32.3 View Transactions
Admins can see:
User
Bin
Deposit session
Transaction type
Amount
Token reason
Verification method
Verification status
Created time

## 33. Database Design

### 33.1 Users
id
fullName
email
passwordHash
role: user/admin
tokenBalanceCached
totalTokensEarned
status: active/suspended
createdAt
updatedAt
Important:
tokenBalanceCached is only for fast display.
TokenTransactions are the source of truth.

### 33.2 TrashBins
id
name
address
latitude
longitude
status: available/full/maintenance/inactive
capacityPercentage
acceptedExtraTokenItems
qrCodeId
qrStatus: active/rotated/disabled
lastQrRotatedAt
lastUpdatedAt
deviceId
createdAt
updatedAt
Example:
{
"acceptedExtraTokenItems": ["accepted_clear_plastic product"]
}

### 33.3 Devices
id
deviceId
deviceTokenHash
binId
status: active/inactive
lastSeenAt
createdAt
updatedAt
Purpose:
Authenticate hardware.
Ensure device can only send events for its assigned bin.

### 33.4 DepositSessions
This is the central model connecting QR scan, hardware event, and reward decision.
id
userId
binId
qrCodeId
status: waiting_for_item/rewarded/expired/rejected
startedAt
expiresAt
itemDetectedAt
irDetected
weightDelta
cameraPredictedClass
cameraConfidence
cameraStatus: accepted_clear_plastic product/uncertain/not_accepted/camera_failed
rewardDecision: no_reward/valid_deposit/accepted_plastic product
tokensAwarded
decisionReason
tokenTransactionIds
createdAt
updatedAt
Example:
rewardDecision = accepted_plastic product
tokensAwarded = 2
decisionReason = camera_confidence_0.82

### 33.5 HardwareEvents
Stores combined deposit result events from the bin.
id
eventId
eventType: deposit_result
binId
depositSessionId
linkedToSession: true/false
sensorType: combined
irDetected
cameraPredictedClass
cameraConfidence
cameraStatus
imageUrl
weightBefore
weightAfter
weightDelta
createdAt

### 33.6 ProcessedHardwareEvents
Optional but useful for idempotency.
eventId
binId
processedAt
depositSessionId

### 33.7 TokenTransactions
Use one currency but store the reason clearly.
id
userId
binId
depositSessionId
transactionType: earn/spend/admin_adjustment
amount
tokenReason: valid_deposit/accepted_item_extra/voucher_redemption/admin_adjustment
verificationMethod: manual/ir/camera/weight/admin
verificationStatus: confirmed/rejected/pending
recognizedItemType
recognitionConfidence
description
createdAt
Example 1:
{
"transactionType": "earn",
"amount": 1,
"tokenReason": "valid_deposit",
"verificationMethod": "ir",
"verificationStatus": "confirmed",
"description": "Valid deposit detected"
}
Example 2:
{
"transactionType": "earn",
"amount": 1,
"tokenReason": "accepted_item_extra",
"verificationMethod": "camera",
"verificationStatus": "confirmed",
"recognizedItemType": "accepted_clear_plastic product",
"recognitionConfidence": 0.82,
"description": "Accepted clear plastic product detected"
}
Example 3:
{
"transactionType": "spend",
"amount": -20,
"tokenReason": "voucher_redemption",
"verificationStatus": "confirmed",
"description": "Redeemed Coffee Voucher"
}

### 33.8 Vouchers
id
name
description
partnerName
requiredTokens
quantityAvailable
expiryDate
termsAndConditions
redemptionInstructions
redemptionDisplayText
sharedCode
maxRedemptionsPerUser
voucherType: shared_code/unique_code
status: active/inactive/expired
createdAt
updatedAt
For Prototype v0:
Use shared_code.
Example:
{
"voucherType": "shared_code",
"sharedCode": "PLASTIQUE-DEMO-COFFEE",
"redemptionInstructions": "Show this code at the partner counter.",
"redemptionDisplayText": "Demo coffee voucher"
}
For later production:
Use unique_code with VoucherCodes collection.

### 33.9 VoucherCodes
Use later if unique voucher codes are needed.
id
voucherId
code
status: available/assigned/used/expired
assignedToUserId
assignedAt
usedAt
createdAt
updatedAt

### 33.10 Redemptions
id
userId
voucherId
voucherCodeId
tokensSpent
status: active/used/expired/cancelled
redeemedAt
expiresAt
Rule:
voucherCodeId = null when voucherType = shared_code

### 33.11 BinReports
Optional for later.
id
userId
binId
reportType: full/broken/qr_damaged/wrong_location/unclear_instruction/other
description
status: open/reviewed/resolved
createdAt
updatedAt

### 33.12 AdminAuditLogs
Optional for later.
id
adminId
actionType
targetType
targetId
reason
createdAt
Use this for:
Manual token adjustment
Voucher modification
Bin status changes
Transaction review

## 34. Required Database Indexes

**DepositSessions**
binId + status
Purpose:
Find active session for bin quickly.
Partial unique index:
Only one waiting_for_item session per bin.

**HardwareEvents**
eventId unique
Purpose:
Prevent duplicate event processing.

**TokenTransactions**
userId + createdAt
Purpose:
Token history and daily limit checks.
depositSessionId
Purpose:
Group activity by deposit session.

**Vouchers**
status
expiryDate
Purpose:
Filter active vouchers.

## 35. API Plan

### 35.1 Auth APIs
POST /api/auth/signup
POST /api/auth/login
GET /api/auth/me
POST /api/auth/logout

### 35.2 Public/User Bin APIs
GET /api/bins
GET /api/bins/:id
For later map version:
GET /api/bins/nearby?lat=13.7563&lng=100.5018&radiusKm=5
Nearby response should include:
binId
name
address
latitude
longitude
distanceMeters
status
acceptedExtraTokenItems
capacityPercentage
lastUpdatedAt
For Prototype v0, static bin list is enough.

### 35.3 QR Scan / Deposit Session APIs
POST /api/recycling/scan
GET /api/deposit-sessions/:id/status
POST /api/dev/simulate-deposit-event
Note:
/api/dev/simulate-deposit-event is only for Prototype v0.
Remove or strongly protect it before production.

**POST /api/recycling/scan**
Purpose:
Validate QR code.
Check bin status.
Check daily limit for UX.
Create DepositSession.
Start 60-second item detection window.
Request:
{
"qrCodeId": "BIN_QR_123"
}
Response:
{
"sessionId": "SESSION_123",
"status": "waiting_for_item",
"message": "QR scanned. Please insert one empty clear plastic product within 60 seconds."
}

**GET /api/deposit-sessions/:id/status**
Example rewarded response:
{
"status": "rewarded",
"tokensEarned": 2,
"message": "Accepted clear plastic product detected. You earned 2 Plastique Tokens total."
}
Example waiting response:
{
"status": "waiting_for_item",
"tokensEarned": 0,
"message": "Please insert one empty clear plastic product within 60 seconds."
}
Example expired response:
{
"status": "expired",
"tokensEarned": 0,
"message": "No item was detected within 60 seconds. No token was awarded."
}

### 35.4 Hardware APIs
Add in Prototype v1:
POST /api/iot/deposit-events
Add in Prototype v2:
POST /api/iot/upload-image
or include image upload inside:
POST /api/iot/deposit-events
Recommended v2 flow:
POST /api/iot/upload-image
→ returns imageUrl and camera classification result
POST /api/iot/deposit-events
→ sends eventId, binId, irDetected, cameraStatus, cameraConfidence, imageUrl

### 35.5 Token and Activity APIs
GET /api/tokens/balance
GET /api/activity
Do not create a public endpoint like:
POST /api/tokens/earn
Token earning must happen through deposit-session and hardware-event logic only.

### 35.6 Activity API
Create a grouped activity endpoint.
GET /api/activity
Response:
{
"items": [
{
"type": "deposit",
"depositSessionId": "SESSION_123",
"tokensEarned": 2,
"title": "Accepted clear plastic product detected",
"binName": "KMITL Smart Bin A",
"createdAt": "2026-07-10T14:30:00Z",
"breakdown": [
"+1 token for valid deposit",
"+1 extra token for accepted clear plastic product"
]
},
{
"type": "redemption",
"redemptionId": "RED_001",
"tokensSpent": 20,
"title": "Redeemed Coffee Voucher",
"createdAt": "2026-07-10T15:00:00Z"
}
]
}
Do not force frontend to group raw token transactions manually.

### 35.7 Voucher APIs
GET /api/vouchers
GET /api/vouchers/redeemable
POST /api/vouchers/:id/redeem
GET /api/redemptions
GET /api/redemptions/:id

### 35.8 Admin APIs
Admin needs full lists, including inactive or disabled records.
GET /api/admin/bins
POST /api/admin/bins
PUT /api/admin/bins/:id
GET /api/admin/vouchers
POST /api/admin/vouchers
PUT /api/admin/vouchers/:id
GET /api/admin/transactions
Public APIs may hide inactive data:
GET /api/bins
GET /api/vouchers
Admin APIs should show full operational data.

## 36. Backend Decision Flow

### 36.1 QR Scan Flow
onQrScan(userId, qrCodeId):
if user is not logged in:
return login_required
bin = find bin by qrCodeId
if QR code is invalid:
return invalid_qr
if bin.status == full:
return bin_full
if bin.status == maintenance:
return bin_maintenance
if bin.status == inactive:
return bin_inactive
if bin already has active waiting_for_item session:
return bin_busy
if user already reached demo reward limit for this bin today:
return daily_bin_limit_reached
create DepositSession:
userId = userId
binId = bin.id
qrCodeId = qrCodeId
status = waiting_for_item
startedAt = now
expiresAt = now + 60 seconds
return:
sessionId
"QR scanned. Please insert one empty clear plastic product within 60 seconds."

### 36.2 Hardware Event Flow
onHardwareEvent(event):
authenticate device token
validate event payload
if eventId already processed:
ignore event
return duplicate_event_ignored
store HardwareEvent
session = find active DepositSession where:
binId = event.binId
status = waiting_for_item
expiresAt > now
if session does not exist:
mark HardwareEvent as unlinked
return no_token_no_active_session
if session.status == rewarded:
return no_token_already_rewarded
if now > session.expiresAt:
close session as expired
return no_token_session_expired
if user already reached demo bin limit:
close session as rejected
return no_token_daily_bin_limit_reached
rewardDecision = decideReward(session, event, prototypeMode)
create TokenTransactions based on rewardDecision
update DepositSession:
status = rewarded or rejected
rewardDecision
tokensAwarded
decisionReason
tokenTransactionIds
update tokenBalanceCached
mark eventId as processed

## 37. Voucher Redemption Logic
Voucher redemption must be safe and atomic.
Backend should:
1. Check user is logged in.
2. Check voucher exists.
3. Check voucher is active.
4. Check voucher is not expired.
5. Check voucher quantity is available.
6. Check user has enough confirmed tokens.
7. Check max redemption per user.
8. Deduct tokens by creating a spending transaction.
9. Reduce voucher quantity.
10. Create redemption record.
11. Return redemption instruction or code.
Important:
Voucher redemption must use an atomic transaction or equivalent locking logic to avoid
overselling vouchers.
For shared-code voucher:
voucherCodeId = null
sharedCode is shown to user
For unique-code voucher later:
assign available VoucherCode
mark VoucherCode as assigned
connect VoucherCode to Redemption

## 38. Voucher Economy
Since one valid accepted plastic product can earn up to 2 tokens, voucher prices should not be
too cheap.
Recommended starting economy:
Valid deposit = +1 token
Accepted clear plastic product extra = +1 token
Maximum per deposit = 2 tokens
Demo same-bin limit = once per user per bin per day
Real product daily cap = 10–20 tokens
Small voucher cost = 20 tokens
Medium voucher cost = 50 tokens
Large voucher cost = 100 tokens
Avoid:
Voucher cost = 5 tokens
because users may redeem too easily and abuse the reward system.

## 39. UI/UX Rules

### 39.1 Mobile-First Design
The system will be used near physical bins, so the UI must be mobile-first.
Design rules:
Large buttons
Simple text
Fast loading
Few steps
Clear status messages
Minimal typing
Readable outdoors

### 39.2 Home Page Must Not Become a Dashboard
First screen should show only:
Search / use my location
Nearest bin or bin list
Token balance if logged in
Do not overload it with:
Leaderboard
Competition
Voucher preview
Long guide
Large analytics

### 39.3 Guide Should Be Embedded
Do not force users to study a separate guide page first.
Put instructions inside:
Bin detail page
Scan flow
Deposit status page
The most important instruction:
Insert only one empty clear plastic product at a time.

### 39.4 User-Friendly Status Labels
Database may use:
confirmed
pending
rejected
But the UI should show:
Token earned
Waiting for review
No token awarded

## 40. Error States

### 40.1 Bin List / Map
Handle:
Location permission denied
No bins found nearby
Map failed to load
Search returned no results
Bin data could not be updated

### 40.2 QR Scan
Handle:
Invalid QR code
QR code belongs to inactive bin
Bin is full
Bin is under maintenance
Bin is currently processing another deposit
Already reached bin reward limit today
Network error during scan
Login required

### 40.3 Deposit Session
Handle:
No item detected
Session expired
Late item insertion
Hardware event without active session
Camera failed
Wrong item detected
Duplicate event

### 40.4 Rewards
Handle:
Not enough tokens
Voucher out of stock
Voucher expired
Redemption failed
Voucher code unavailable

### 40.5 Activity
Handle:
No activity yet
Could not load transaction history

## 41. Security Requirements
Minimum requirements:
Password hashing with bcrypt
JWT authentication
Role-based access control
Input validation
Rate limiting
Protected admin routes
Device token/API key for hardware endpoint
No public token-earning endpoint
Atomic reward creation
Atomic voucher redemption
Duplicate event protection
Admin audit logs later
Backend rules:
User endpoints require authentication.
Admin endpoints require role = admin.
IoT endpoints require device authentication.
Frontend rule:
Hide admin navigation from normal users.
Important:
Frontend hiding is not security. Backend must enforce permissions.

## 42. Recommended Tech Stack

**Frontend**
React.js
TypeScript
Vite
Tailwind CSS
React Router
Axios
Mapbox or Google Maps API later

**Backend**
Node.js
Express.js
JWT
Bcrypt
Zod or Joi
Express Rate Limit

**Database**
MongoDB
MongoDB Atlas

**Hardware**
ESP32 or ESP32-CAM
IR break-beam sensor
Optional load cell + HX711
LED/buzzer

**Deployment**
Frontend: Vercel
Backend: Render / Railway / VPS
Database: MongoDB Atlas

## 43. Final API Contract List for Prototype v0

**Auth**
POST /api/auth/signup
POST /api/auth/login
GET /api/auth/me
POST /api/auth/logout

**Public/User Bins**
GET /api/bins
GET /api/bins/:id

**Deposit Session**
POST /api/recycling/scan
GET /api/deposit-sessions/:id/status
POST /api/dev/simulate-deposit-event
Note:
/api/dev/simulate-deposit-event is only for Prototype v0.
Remove or protect it before production.

**Tokens and Activity**
GET /api/tokens/balance
GET /api/activity

**Vouchers**
GET /api/vouchers
GET /api/vouchers/redeemable
POST /api/vouchers/:id/redeem
GET /api/redemptions

**Admin**
GET /api/admin/bins
POST /api/admin/bins
PUT /api/admin/bins/:id
GET /api/admin/vouchers
POST /api/admin/vouchers
PUT /api/admin/vouchers/:id
GET /api/admin/transactions

## 44. Final API Contract List for Hardware Versions
Add in Prototype v1:
POST /api/iot/deposit-events
Add in Prototype v2:
POST /api/iot/upload-image
or include image upload inside:
POST /api/iot/deposit-events
Recommended v2 flow:
POST /api/iot/upload-image
→ returns imageUrl and camera classification result
POST /api/iot/deposit-events
→ sends eventId, binId, irDetected, cameraStatus, cameraConfidence, imageUrl

## 45. Development Roadmap

**Phase 1: Foundation**
Create frontend project
Create backend project
Connect MongoDB
Set environment variables
Create folder structure

**Phase 2: Authentication**
Signup
Login
JWT
Protected routes
Admin role

**Phase 3: Bins**
Create TrashBins model
Create bin APIs
Build static bin list
Build bin detail page
Add accepted/rejected item content

**Phase 4: QR URL Scan Flow**
Create /scan?qrCodeId=BIN_QR_123 route
Handle logged-out QR flow
After login, resume QR scan
Call POST /api/recycling/scan

**Phase 5: Deposit Session**
Create DepositSessions model
QR scan creates deposit session
Add 60-second expiry
Add one active session per bin rule
Add demo daily user-bin earning limit

**Phase 6: Hardware Event Simulation**
Create HardwareEvents model
Create /api/dev/simulate-deposit-event endpoint
Add eventId duplicate protection
Simulate valid deposit manually first
Award tokens only after valid deposit event

**Phase 7: Frontend Polling**
Create GET /api/deposit-sessions/:id/status
Poll every 1–2 seconds after QR scan
Stop polling after rewarded/rejected/expired
Show final reward message

**Phase 8: Token and Activity**
Create TokenTransactions model
Use signed transaction amounts
Calculate token balance
Group activity by depositSessionId
Show token history
Recalculate cached balance

**Phase 9: Rewards**
Create Vouchers model
Add sharedCode and redemptionDisplayText
Build rewards page
Build redemption confirmation
Create spending transaction
Create redemption record
Use atomic redemption logic

**Phase 10: Admin v0**
Manage bins
Manage vouchers
View transactions

**Phase 11: Real IoT Integration**
Create Devices model
Create POST /api/iot/deposit-events
Authenticate device token
Validate device-to-bin mapping
Process real IR events

**Phase 12: Camera Recognition**
Add ESP32-CAM image capture
Send image to backend or laptop server
Classify accepted_clear_plastic product / uncertain / not_accepted / camera_failed
Award +1 extra token only for accepted_clear_plastic product

**Phase 13: Load Cell Validation**
Add load cell + HX711
Calibrate local plastic product weight range
Reject only clearly abnormal weight
Improve reward confidence

**Phase 14: Testing and Deployment**
Test login/signup
Test QR URL scan
Test logged-out QR resume flow
Test session expiry
Test no-token QR-only case
Test no-token unlinked hardware event case
Test duplicate event protection
Test daily bin limit
Test valid deposit reward
Test accepted plastic product extra token rule
Test voucher redemption
Test admin permissions
Deploy frontend
Deploy backend
Connect production database

## 46. Final Build Priority
Build in this order:
1. Auth
2. Static bin list
3. Bin detail
4. QR URL scan flow
5. DepositSession creation
6. Manual simulated valid deposit event
7. TokenTransactions
8. Token balance
9. Activity page
10. Vouchers
11. Voucher redemption
12. Admin bins
13. Admin vouchers
14. Admin transactions
15. Real IoT deposit-events endpoint
16. ESP32/IR integration
17. Camera classification
18. Load cell validation

## 47. Final Prototype Success Criteria
The prototype is successful if:
1. A user can create an account.
2. A user can view available smart bins.
3. A user can open bin detail.
4. A user can see that only empty clear plastic products are accepted for extra tokens.
5. A user can scan QR URL to start a deposit session.
6. The system does not award tokens for QR scan alone.
7. The system does not award tokens for object detection without a session.
8. The backend can link bin-level hardware events to the active session.
9. The system can reject invalid deposits.
10. The user earns +1 token for a valid deposit event.
11. The user earns +1 extra token if the item is detected as an accepted clear plastic product.
12. The system prevents duplicate hardware-event rewards.
13. The same user cannot exceed the demo reward limit for the same bin.
14. The frontend shows session status through polling.
15. The user can redeem vouchers using Plastique Tokens.
16. The user can view grouped activity history.
17. Admin can manage bins, manage vouchers, and view transactions.

## 48. Final System Summary
The platform uses one currency: Plastique Tokens.
The QR code identifies the user and starts a deposit session. It does not give tokens by itself.
The smart-bin hardware sends bin-level deposit events to the backend. The backend finds the
active session for that bin, links the hardware event to the user, checks whether the deposit is
valid, and awards tokens only once.
A valid deposit earns 1 Plastique Token. If the camera confidently detects an accepted clear
plastic product, the user earns 1 extra Plastique Token, for a maximum of 2 Plastique Tokens
per accepted-plastic product deposit. Static QR codes are acceptable for the prototype, but
production should move toward dynamic QR, stronger hardware validation, or location
checks.
Final explanation for report or presentation:
The QR code identifies the user and starts the session. The smart-bin sensor confirms that a
deposit occurred. The camera estimates whether the deposit is an accepted clear plastic
product. The backend links the hardware event to the active session and awards tokens only
once.
