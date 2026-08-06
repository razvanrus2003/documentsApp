# Walkthrough - Split Signature Modes

I have refactored the Signature screen into a selection menu and added dedicated pages for Digital and SVG signatures.

## Changes Made

### 1. New Screens & Navigation
- **Digital Signature Page**: Created `DigitalSignatureFragment` and its layout for handling digital signature keys.
- **SVG Signature Page**: Created `SvgSignatureFragment` and its layout for SVG-based signatures.
- **Updated Navigation Graph**: Added the new fragments to `nav_graph.xml` with actions from the main Signature selection screen.

### 2. UI Redesign
- **Signature Selection**: Updated `fragment_signature.xml` to feature two large square buttons: **Digital Signature** and **SVG Signature**.
- **Button logic**: Updated `SignatureFragment.kt` to navigate to the specific mode based on user selection.
- **Return Behavior**: Sub-modes now correctly return to the selection screen (or previous screen) when "Save" is pressed using `popBackStack()`.

### 3. Resources
- Added descriptive strings for the new modes in `strings.xml`.

## Verification Results

### Build
- Ran `./gradlew assembleDebug` - **Build Successful**.

### Manual Flow
1.  **Enter Signature**: From Home/Edit, navigate to Signatures.
2.  **Select Mode**: See two square buttons.
3.  **Digital Mode**: Click "Digital Signature" -> Opens Digital screen -> Click "Save" -> Returns.
4.  **SVG Mode**: Click "SVG Signature" -> Opens SVG screen -> Click "Save" -> Returns.

> [!TIP]
> The sub-modes use `popBackStack()`, so if you entered Signature from the Edit screen, saving a specific signature will eventually lead you back to your document.
