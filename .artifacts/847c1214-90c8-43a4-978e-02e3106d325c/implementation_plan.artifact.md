# Signature List and UI Standardization

This plan introduces a "My Signatures" list view and standardizes the app's UI to use icon-only, square buttons.

## User Review Required

> [!IMPORTANT]
> **Button Style**: I will apply `app:cornerRadius="0dp"` and `app:iconPadding="0dp"` to all buttons to ensure a strict square look. All text will be removed and replaced with representative icons.
> **Navigation Change**: The "Signatures" section in the drawer will now lead to `SignatureListFragment`. The "Create New" button in that list will lead to the type selection screen.
> **Signature Storage**: The list will scan the `files/signatures/` directory for SVG files.

## Proposed Changes

### [Component] Signature Management

#### [NEW] [SignatureListFragment.kt](file:///home/razvan/Documents/DocumentsApp/app/src/main/java/com/example/documentsapp/ui/SignatureListFragment.kt) & [fragment_signature_list.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/layout/fragment_signature_list.xml)
- Display a grid or list of saved signatures.
- Add a square "Create New" button (icon: `ic_input_add`).

#### [MODIFY] [nav_graph.xml](file:///home/razvan/Documents/DocumentsApp/app/src/main/res/navigation/nav_graph.xml)
- Set `SignatureListFragment` as the destination for `nav_signature`.
- Update actions to flow: List -> Type Selection -> Drawing Page.

### [Component] UI Standardization (Icon-only, Square)

I will update the following fragments to use icon-only square buttons:

#### 1. HomeFragment
- **Scan**: `ic_menu_camera`
- **Import**: `ic_menu_add`

#### 2. EditFragment
- **Crop**: `ic_menu_crop` (if available, otherwise custom)
- **Rotate**: `ic_menu_rotate`
- **Adjust**: `ic_menu_manage`
- **Add Signature**: `ic_menu_edit`
- **Cancel**: `ic_menu_close_clear_cancel`
- **Save**: `ic_menu_save`
- **Export**: `ic_menu_share`

#### 3. CameraFragment
- **Capture**: `ic_menu_camera`
- **Flash**: `ic_menu_info_details` (will find a better one)

#### 4. Svg/Digital Fragments
- **Clear**: `ic_menu_revert`
- **Save**: `ic_menu_save`
- **Eraser**: `ic_menu_delete`

### [Component] Common Styling
- I will ensure every button uses:
    - `style="@style/Widget.Material3.Button.TonalButton"`
    - `app:cornerRadius="0dp"`
    - `android:text=""`
    - `app:icon="..."`
    - `android:contentDescription="..."` (for accessibility)

## Verification Plan

### Manual Verification
1.  **Button Check**: Browse every screen. Verify all buttons are square and contain only icons.
2.  **Signature List**: Save a signature -> Go to Signatures list -> Verify it appears there.
3.  **New Signature Flow**: From list -> Press [+] -> Choose Type -> Draw -> Save -> Returned to list.
