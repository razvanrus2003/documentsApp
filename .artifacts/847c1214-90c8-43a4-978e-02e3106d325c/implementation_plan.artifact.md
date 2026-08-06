# Refactor Signature Selection and Add Sub-modes

This plan describes the refactoring of the Signature screen into a selection menu with two options: Digital Signature and SVG Signature. Each option will lead to its own dedicated page.

## Proposed Changes

### [Component] Resources & Navigation

#### [MODIFY] [strings.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/values/strings.xml)
- Add labels for "Digital Signature" and "SVG Signature" buttons.
- Add titles for the new screens.

#### [MODIFY] [nav_graph.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/navigation/nav_graph.xml)
- Add `DigitalSignatureFragment` destination.
- Add `SvgSignatureFragment` destination.
- Add actions from `SignatureFragment` to these new destinations.

### [Component] UI & Fragments

#### [MODIFY] [fragment_signature.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/layout/fragment_signature.xml)
- Redesign to feature two large square buttons side-by-side or stacked.
- Remove the drawing canvas placeholder from this selection screen.

#### [MODIFY] [SignatureFragment.kt](file:///home/razvan/Documents/DocumentsApp/app/src/main/java/com/example/documentsapp/SignatureFragment.kt)
- Set up click listeners for the two new buttons to navigate to their respective sub-modes.

#### [NEW] [DigitalSignatureFragment.kt](file:///home/razvan/Documents/DocumentsApp/app/src/main/java/com/example/documentsapp/DigitalSignatureFragment.kt) & [fragment_digital_signature.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/layout/fragment_digital_signature.xml)
- A screen similar to the previous signature page, focused on digital keys.

#### [NEW] [SvgSignatureFragment.kt](file:///home/razvan/Documents/DocumentsApp/app/src/main/java/com/example/documentsapp/SvgSignatureFragment.kt) & [fragment_svg_signature.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/layout/fragment_svg_signature.xml)
- A screen similar to the previous signature page, focused on SVG path drawing/importing.

## Verification Plan

### Manual Verification
1.  **Selection Screen**: Navigate to Signature (from Home or Edit). Verify you see two square buttons: "Digital Signature" and "SVG Signature".
2.  **Digital Flow**: Tap "Digital Signature" -> Verify it opens the digital signature page -> Tap "Save" -> Should return to previous screen.
3.  **SVG Flow**: Tap "SVG Signature" -> Verify it opens the SVG signature page -> Tap "Save" -> Should return to previous screen.
4.  **Backstack**: Verify that pressing "Back" from either sub-mode returns you to the Selection screen, and "Back" from Selection returns you to Home/Edit.
