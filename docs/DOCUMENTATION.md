# DocumentsApp — Technical & Architectural Documentation 📖✨

Welcome to the comprehensive technical documentation for **DocumentsApp**, an advanced Android document scanner, multi-page image editor, vector SVG signature canvas, and cryptographic PDF digital signing solution.

---

## 📋 Table of Contents
1. [Architecture & System Overview](#-architecture--system-overview)
2. [Component Architecture & UI Layer](#-component-architecture--ui-layer)
3. [Smart Document Scanning & Edge Detection](#-smart-document-scanning--edge-detection)
4. [Multi-Page Editing & Image Processing Engine](#-multi-page-editing--image-processing-engine)
5. [PDF Generation & Interactive Viewing Engine](#-pdf-generation--interactive-viewing-engine)
6. [Signature Engine (SVG Vector & Cryptographic P12)](#-signature-engine-svg-vector--cryptographic-p12)
7. [Data Persistence & Storage Management](#-data-persistence--storage-management)
8. [Testing Strategy & CI/CD Pipeline](#-testing-strategy--cicd-pipeline)
9. [Security, Privacy & Scoped Storage](#-security-privacy--scoped-storage)

---

## 🏗️ Architecture & System Overview

DocumentsApp is engineered using modern Android development practices, following the **Single-Activity Architecture**, **Model-View-ViewModel (MVVM)** design pattern, and Jetpack Navigation principles.

### Tech Stack & System Specifications

| Layer / Aspect | Tech Stack / Tooling |
| :--- | :--- |
| **Language** | Kotlin 1.9+ |
| **Min / Target / Compile SDK** | SDK 24 (Android 7.0) / SDK 36 (Android 15) |
| **Architecture Pattern** | Single Activity + Jetpack Navigation + MVVM |
| **Computer Vision Engine** | OpenCV 4.x (`OpenCVLoader.initLocal()`) |
| **Camera Framework** | AndroidX CameraX (`core`, `camera2`, `lifecycle`, `view`) |
| **PDF Engine** | Native `android.graphics.pdf` + Apache PDFBox Android (`pdfbox-android`) |
| **Cryptographic Engine** | BouncyCastle (`bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on`) |
| **Testing Harness** | JUnit 4, Robolectric 4.11+, Mockito, AndroidX Arch Core Testing |
| **Build & CI** | Gradle Kotlin DSL, Custom `runAllTests` task, GitHub Actions |

---

## 🧩 Component Architecture & UI Layer

```
com.example.documentsapp
├── MainActivity.kt                  # Single Activity hosting NavHostFragment & Drawer
├── DocumentsApp.kt                  # Application class
├── data/
│   └── DocumentManager.kt           # Central persistence manager & MediaStore bridge
├── ui/
│   ├── DocumentViewModel.kt         # Shared ViewModel for scan & edit session state
│   ├── SignatureListFragment.kt     # Saved signatures repository view
│   ├── SignatureDrawingView.kt     # Interactive SVG vector canvas drawing view
│   ├── SignatureOverlayView.kt     # Interactive PDF signature overlay (drag/scale/rotate)
│   ├── CropOverlayView.kt          # Manual crop quad handles & magnifier loupe view
│   ├── ZoomableLayout.kt            # Custom gesture view for PDF zoom & pan
│   ├── ColorHueSliderView.kt       # HSV Hue selector view
│   ├── SaturationValueMapView.kt   # HSV Saturation/Value selector view
│   └── Adapters...                 # DocumentAdapter, FilterAdapter, ImagePageAdapter, PdfPageAdapter
├── utils/
│   ├── DocumentDetector.kt         # OpenCV contour & quadrilateral detection algorithm
│   ├── ImageUtils.kt               # OpenCV filter suite & perspective transform deskewing
│   ├── P12Generator.kt             # BouncyCastle X.509 certificate & PKCS#12 generator
│   ├── PdfGenerator.kt             # Bitmap list to A4 PDF compiler
│   ├── PdfSigner.kt                # Apache PDFBox cryptographic PDF digital signature applier
│   ├── SvgExporter.kt              # Stroke list to XML SVG serializer
│   ├── SvgUtils.kt                 # SVG rendering to Bitmap engine
│   └── ViewExtensions.kt           # Edge-to-Edge window insets utility functions
└── Fragments...                    # HomeFragment, CameraFragment, CropFragment,
                                     # EditImagesFragment, PdfViewFragment, SignatureFragment,
                                     # DigitalSignatureFragment, SvgSignatureFragment, etc.
```

### Shared State & Activity Lifecycle
* **Shared ViewModel (`DocumentViewModel`)**: Scoped to the host activity (`activityViewModels()`). Maintains in-memory page models (`PageItem`), current filter states, retake indices, and active PDF zoom coordinates across `CameraFragment`, `CropFragment`, `EditImagesFragment`, and `PdfViewFragment`.

---

## 📷 Smart Document Scanning & Edge Detection

The scanning module combines CameraX hardware acceleration with OpenCV computer vision analysis to automatically detect, crop, and deskew document pages in real time.

```
[CameraX Feed] ---> [ImageAnalysis Analyzer] ---> [DocumentDetector (OpenCV Pipeline)]
                                                            |
                                                            v
[CameraOverlayView (Live Quad Draw)] <--- [Exponential Moving Average (EMA) Smoothed Quad]
```

### 🔍 Step-by-Step Document Detection Pipeline (`DocumentDetector`)

Real-time document detection operates on incoming camera preview frames through a multi-stage computer vision pipeline:

1. **Frame Decomposition & Channel Splitting**:
   Input RGB/YUV camera frames are split into individual 8-bit single channels (`Core.split`). Processing individual channels ensures robust edge detection under non-uniform colored lighting (e.g., warm indoor lighting or blue cast).
2. **Noise Reduction (Median Blur Filtering)**:
   A 5x5 median blur filter (`Imgproc.medianBlur(channel, blurred, 5)`) suppresses sensor grain and high-frequency noise while strictly preserving sharp document edge boundaries.
3. **Binary Segmentation & Morphological Sequence**:
   - Global thresholding (`Imgproc.threshold`) at intensity value 160.0 segments high-luminance paper surfaces from darker backgrounds.
   - Morphological operations sequence (`dilate(1)` -> `erode(1)` -> `dilate(1)`) using a 3x3 structuring element kernel bridges small line breaks along document edges and eliminates small interior noise voids.
4. **Multi-Pass Iterative Canny Edge Detection**:
   To catch low-contrast document borders on textured desks, multi-pass Canny edge detection (`Imgproc.Canny`) runs at decreasing threshold levels t in {100, 60, 20} with high threshold 2t. Canny edge lines are dilated (`Imgproc.dilate`) to enforce closed boundary loops.
5. **Contour Extraction & Area Pre-Filtering**:
   `Imgproc.findContours` extracts closed curves using `RETR_EXTERNAL` topological mode and `CHAIN_APPROX_SIMPLE` point compression. Contours with a raw area smaller than 5% of total frame area (Area_img = W * H) are immediately rejected to optimize performance.
6. **Ramer-Douglas-Peucker Polygon Approximation**:
   Filtered contours are converted to 2D floating-point coordinates (`MatOfPoint2f`) and simplified using the Ramer-Douglas-Peucker algorithm (`Geometry.approxPolyDP`) with an epsilon distance parameter epsilon = 0.02 * Perimeter.
7. **Quadrilateral Validation & Geometric Constraints**:
   Candidates must pass strict geometric criteria:
   - **Vertex Count**: Polyline must have exactly 4 corner points (`approxArray.size == 4`).
   - **Area Boundary**: Quadrilateral area must occupy between 5% and 95% of total frame area (0.05 * Area_img < Area_quad < 0.95 * Area_img).
   - **Convexity Constraint**: Verified via `Geometry.isContourConvex` to reject self-intersecting shapes.
   - **Orthogonality / Interior Angle Constraint**: Calculates interior angle cosines cos(theta) = (u . v) / (||u|| ||v||). Candidates with max(cos(theta)) >= 0.3 (interior angles departing significantly from 90 degrees) are discarded.
8. **Candidate Quality Scoring & Ranking**:
   Valid candidates are ranked using a squareness-weighted quality metric:
   $\text{sortFactor} = \text{Area} \cdot (1.0 - 2.0 \cdot \max(\cos\theta)) + 0.1 \cdot \text{Weight}$
9. **Low-Pass Exponential Moving Average (EMA) Corner Stabilization**:
   To eliminate spatial jitter caused by handheld camera tremor, detected corner coordinates P_target are smoothed against the previous frame P_current using an EMA low-pass filter (alpha = 0.7):
   $P_{\text{smooth}} = P_{\text{current}} \cdot (1 - \alpha) + P_{\text{target}} \cdot \alpha$

---

### 🔍 Interactive Corner Cropping & Sub-Pixel Magnifier (`CropOverlayView`)

In **Manual Capture Mode**, users can manually refine the 4 document corners:
* **Interactive Quad Handles**: Touch handles allow dragging corners independently across the image preview.
* **Sub-Pixel Magnifier Loupe**: When a corner handle is pressed, an amplified circular loupe (2.0x zoom) renders the bitmap region immediately surrounding the touch point, overcoming finger obstruction to achieve pixel-perfect corner placement.

---

### 📐 Perspective Transformation & Deskewing (`ImageUtils.cropAndEnhance`)

Once document corner coordinates are finalized, perspective distortion is mathematically corrected to transform tilted paper photos into flat, rectangular A4 pages.

#### Mathematical Principles & Algorithms Used

1. **2D Planar Homography Transformation**:
   A 2D projective transformation maps arbitrary source quadrilateral points p_i = (x_i, y_i, 1)^T to target rectangular destination points p'_i = (x'_i, y'_i, 1)^T via a 3x3 non-singular Homography matrix H:
   $\begin{bmatrix} x' \\ y' \\ 1 \end{bmatrix} \sim H \begin{bmatrix} x \\ y \\ 1 \end{bmatrix} = \begin{bmatrix} h_{11} & h_{12} & h_{13} \\ h_{21} & h_{22} & h_{23} \\ h_{31} & h_{32} & h_{33} \end{bmatrix} \begin{bmatrix} x \\ y \\ 1 \end{bmatrix}$

2. **Direct Linear Transform (DLT) Matrix Computation**:
   Setting scale parameter h_33 = 1 leaves 8 degrees of freedom. Each corner correspondence yields two independent linear equations:
   $x'_i = \frac{h_{11}x_i + h_{12}y_i + h_{13}}{h_{31}x_i + h_{32}y_i + 1}, \quad y'_i = \frac{h_{21}x_i + h_{22}y_i + h_{23}}{h_{31}x_i + h_{32}y_i + 1}$
   `Geometry.getPerspectiveTransform` solves H using Singular Value Decomposition (SVD) on the resulting system of 8 linear equations.

3. **Backward Image Warping & Bilinear Interpolation**:
   `Imgproc.warpPerspective` computes inverse mapping p_src = H^-1 p_dst for every destination pixel (x', y'). Fractional source coordinates (x, y) are sampled using **Bilinear Interpolation** to eliminate aliasing artifacts:
   $f(x,y) \approx (1-u)(1-v) f(x_0, y_0) + u(1-v) f(x_1, y_0) + (1-u)v f(x_0, y_1) + uv f(x_1, y_1)$

4. **Aspect-Ratio Compensated Spatial Coordinate Mapping**:
   Corner coordinates measured on preview analysis frames (W_ana, H_ana) are mapped to full camera sensor resolution (W_bmp, H_bmp) via `mapAnalysisToBitmap`:
   $\text{Scale} = \max\left(\frac{H_{\text{bmp}}}{H_{\text{ana}}}, \frac{W_{\text{bmp}}}{W_{\text{ana}}}\right)$
   $\begin{bmatrix} X_{\text{bmp}} \\ Y_{\text{bmp}} \end{bmatrix} = \text{Scale} \cdot \begin{bmatrix} X_{\text{ana}} \\ Y_{\text{ana}} \end{bmatrix} - \begin{bmatrix} \text{Offset}_X \\ \text{Offset}_Y \end{bmatrix}$

---

## 📄 Multi-Page Editing & Image Processing Engine

The multi-page editor (`EditImagesFragment`) provides high-performance document page reordering, rotation, retaking, deletion, and real-time image filtering.

### 🧪 Custom OpenCV Filter Suite Specifications (`ImageUtils`)

All image processing algorithms are executed natively via OpenCV C++ acceleration wrappers:

#### 1. Black & White (B&W)
* **Kernel / Formula**:
  $\sigma_b^2(t) = \omega_0(t) \omega_1(t) [\mu_0(t) - \mu_1(t)]^2$
  $I_{\text{dst}}(x,y) = \begin{cases} 255 & \text{if } I_{\text{gray}}(x,y) > T^* \\ 0 & \text{otherwise} \end{cases}$
* **Algorithm Explanation**: Converts RGB to 8-bit single-channel grayscale (`COLOR_RGB2GRAY`). When no manual threshold is provided, Otsu's algorithm evaluates the intensity histogram to automatically calculate the optimal global threshold T* that maximizes variance between background and foreground text pixels.

#### 2. Blur Remover
* **Kernel / Formula**:
  $K = \begin{bmatrix} 0 & -1 & 0 \\ -1 & 5 & -1 \\ 0 & -1 & 0 \end{bmatrix}$
  $I_{\text{dst}}(x,y) = \sum_{i=-1}^1 \sum_{j=-1}^1 K(i+1, j+1) \cdot I_{\text{src}}(x+i, y+j)$
* **Algorithm Explanation**: Applies 2D spatial convolution (`Imgproc.filter2D`). The center weight +5 amplifies the target pixel intensity while subtracting the 4-cardinal spatial neighbors, sharpening text edges and recovering motion-blurred characters.

#### 3. Equalizer
* **Kernel / Formula**:
  $T(k) = \text{round}\left( \frac{\text{CDF}(k) - \text{CDF}_{\min}}{(W \times H) - \text{CDF}_{\min}} \times 255 \right)$
* **Algorithm Explanation**: Converts RGB to CIELAB color space (`COLOR_RGB2Lab`). Applies histogram equalization (`Imgproc.equalizeHist`) strictly to the L* lightness channel, spreading non-uniform shadow/lighting intensity distributions across [0, 255] without distorting chrominance colors.

#### 4. Greyscale
* **Kernel / Formula**:
  $Y(x,y) = 0.299 R(x,y) + 0.587 G(x,y) + 0.114 B(x,y)$
* **Algorithm Explanation**: Drops color channels and projects RGB color values into luminance intensity based on human spectral eye sensitivity (heaviest weighting to green).

#### 5. Invert
* **Kernel / Formula**:
  $I_{\text{dst}}(x,y) = 255 - I_{\text{src}}(x,y)$
* **Algorithm Explanation**: Reverses all color values across channels, producing a dark-mode styled document view.

#### 6. Sketch
* **Kernel / Formula**:
  $G(x,y) = \frac{1}{2\pi \sigma^2} e^{-\frac{x^2 + y^2}{2\sigma^2}}$
  $I_{\text{sketch}}(x,y) = \min\left(255, \frac{I_{\text{gray}}(x,y) \cdot 256}{255 - I_{\text{blur}}(x,y) + 1}\right)$
* **Algorithm Explanation**: Inverts the grayscale image, applies a heavy 21x21 Gaussian blur (`Imgproc.GaussianBlur`), inverts the blur, and applies a Color Dodge division blend to isolate sharp edge lines while washing out flat backgrounds.

#### 7. Brightness
* **Kernel / Formula**:
  $g(x,y) = 1.0 \cdot f(x,y) + 30.0$
* **Algorithm Explanation**: Adds a positive bias offset (beta = 30.0) directly to pixel intensities, brightening underexposed documents.

#### 8. Contrast
* **Kernel / Formula**:
  $g(x,y) = 1.5 \cdot f(x,y) + 0.0$
* **Algorithm Explanation**: Applies a linear gain factor (alpha = 1.5) to scale pixel intensities, expanding the dynamic range to make faint text stand out.

---

## 🛠️ PDF Generation & Interactive Viewing Engine

### 1. Document Compiler (`PdfGenerator`)
Converts processed page images into a multi-page PDF document:
* Pages are scaled and formatted to fit standard **A4 dimensions** (595 x 842 points at 72 DPI) while maintaining original aspect ratios.
* Pages are centered on the canvas using `android.graphics.pdf.PdfDocument`.

### 2. High-Performance PDF Viewer (`PdfViewFragment`)
* Renders PDF pages asynchronously using `android.graphics.pdf.PdfRenderer`.
* Recycles page bitmaps dynamically via `PdfPageAdapter` to minimize RAM footprint during long document scrolling.
* Supports inline document renaming directly from the toolbar, synchronizing MediaStore and internal storage references seamlessly.

### 3. Touch Gesture Control (`ZoomableLayout`)
* Implements `ScaleGestureDetector` and `GestureDetector` for smooth pinch-to-zoom (up to 5.0x scale) and pan gestures.
* Clamps translation bounds dynamically to keep content within view boundaries.
* Double-tap gesture toggles between 1.0x default zoom and 2.5x magnified view.

---

## ✍️ Signature Engine (SVG Vector & Cryptographic P12)

DocumentsApp features a dual-mode signature architecture: **Visual Vector Signatures (SVG)** and **Cryptographic Digital Signatures (PKCS#12)**.

```
                         ┌───> [SVG Canvas Drawing] ───> SvgExporter (.svg XML) ───┐
                         │                                                         │
[Signature Selection] ───┼───> [P12 Form Generator] ──> P12Generator (.p12) ──────┼───> [SignatureListFragment] ───> [PdfSigner]
                         │                                                         │
                         └───> [Import File] ─────────> (.svg / .p12 / .pfx) ──────┘
```

### ✏️ Implementation of Drawing Tools (`SignatureDrawingView`)

The vector drawing view implements three specialized tools operating on a hardware-accelerated ARGB_8888 canvas layer (`setLayerType(LAYER_TYPE_HARDWARE, null)`):

1. **Pen Tool**:
   - **Smooth Quadratic Bézier Curve Interpolation**: Motion events (`ACTION_MOVE`) compute midpoints M = ((X_last + X_curr)/2, (Y_last + Y_curr)/2) and construct smooth quadratic Bézier curves (`Path.quadTo`) to eliminate angular joints:
     $P(t) = (1-t)^2 P_0 + 2(1-t)t P_1 + t^2 P_2$
   - **Velocity-Responsive Dynamic Stroke Width**: Uses `VelocityTracker` to measure touch speed v = sqrt(v_x^2 + v_y^2). Stroke width scales inversely with velocity to emulate natural ink flow:
     $W_{\text{target}} = W_{\text{base}} \cdot \left(1.2 - \mathrm{clamp}\left(\frac{v}{3000}, 0.0, 0.9\right)\right)$
     Smoothly interpolated via EMA: W_curr = 0.6 * W_last + 0.4 * W_target.
   - **Paint Config**: `Paint.Style.STROKE`, `Paint.Cap.ROUND`, `Paint.Join.ROUND` with full anti-aliasing.

2. **Pencil Tool**:
   - **Textured Ribbon-Mesh Rendering Engine**: Instead of basic line paths, the Pencil tool computes perpendicular normal vectors along sub-pixel trajectory points using `PathMeasure`.
   - **Ribbon Edge Geometry**: Along trajectory tangent T = (t_x, t_y), normal vector N = (-t_y, t_x) calculates left and right ribbon boundary edges:
     $L_i = P_i + \vec{N} \cdot \frac{W_i}{2}, \quad R_i = P_i - \vec{N} \cdot \frac{W_i}{2}$
   - **Polygon Quad Fill**: Fills connecting quadrilaterals (`Path.moveTo(L_{i-1})` -> `lineTo(L_i)` -> `lineTo(R_i)` -> `lineTo(R_{i-1})` -> `close()`) with solid fill paint (`Paint.Style.FILL`) and renders joint circles to produce organic graphite pencil stroke textures.

3. **Eraser Tool**:
   - **Hardware-Accelerated Alpha Clearing**: Configures paint transfer mode to `PorterDuffXfermode(PorterDuff.Mode.CLEAR)`.
   - **Direct Alpha Erasing**: Drawing stroke paths (`drawCanvas.drawLine` and `drawCanvas.drawCircle`) directly zeroes pixel alpha values (A = 0), erasing underlying vector strokes back to total transparency.

---

### 🔐 Cryptographic Digital Signatures (`P12Generator` & `PdfSigner`)

* **PKCS#12 / X.509 Certificate Generation (`P12Generator`)**:
  - Uses BouncyCastle Security Provider (`BouncyCastleProvider`).
  - Generates 2048-bit RSA Key Pairs (`KeyPairGenerator`).
  - Issues self-signed X.509 certificates with user-defined Subject parameters (Common Name, Organization, Unit, Location, State, Country).
  - Bundles key and certificate into an encrypted `.p12` keystore with password protection.
* **PDF Cryptographic Signing (`PdfSigner`)**:
  - Integrated with Apache PDFBox Android.
  - Applies CMS/PKCS#7 signatures embedding cryptographic message digests (SHA-256) and X.509 certificate chains into PDF structure.
  - Ensures tamper-evident document integrity verification.

---

## 💾 Data Persistence & Storage Management

### Directory Structure & Android Storage Allocation Strategy

```
Internal Storage (/data/data/com.example.documentsapp/)
├── files/
│   ├── signatures/           # Saved SVG vector signatures (.svg)
│   └── digital_signatures/   # Encrypted PKCS#12 keystores (.p12)
├── cache/                    # Temporary crop images, deskewed files, temp PDFs
└── shared_prefs/
    └── app_prefs.xml         # Recent document URIs index & app configuration
```

### 🛡️ Architectural Arguments Supporting Storage Allocation Strategy

Android's storage subsystem enforces strict process isolation and security boundaries. DocumentsApp's directory strategy is designed around Android's core storage principles:

1. **Linux Process Isolation & UID Sandbox**:
   - Android assigns a unique Linux User ID (UID, e.g., `u0_a182`) to the app upon installation.
   - Kernel-level POSIX file permissions (`rwx------`) restrict access to `/data/data/com.example.documentsapp/` exclusively to the app's process UID.
2. **Persistent Private Storage (`Context.filesDir`)**:
   - `/data/data/com.example.documentsapp/files/` resides on encrypted internal flash storage.
   - **Rationale**: User vector signatures (`files/signatures/*.svg`) and cryptographic certificate keystores (`files/digital_signatures/*.p12`) are stored here because files in `filesDir` are **never pruned by the operating system**. Private key material is permanently protected against inspection by unauthorized third-party apps.
3. **Volatile App Cache (`Context.cacheDir`)**:
   - `/data/data/com.example.documentsapp/cache/` is designated for temporary runtime buffers.
   - **Rationale**: Used for camera capture JPEGs, manual crop deskewed files, and unflattened intermediate PDFs. The Android storage daemon (`vold`) and system Storage Manager automatically purge files in `cacheDir` if device disk space runs low, preventing disk bloat while guaranteeing app stability.
4. **Android Scoped Storage Architecture (API 29+ / Android 10+ & API 30+ / Android 11+)**:
   - **How Android Sees Storage**: Direct raw filesystem path access (e.g. `/sdcard/`, `/storage/emulated/0/`) is forbidden. App sandboxes interact with public storage through system abstractions.
   - **MediaStore Exports**: Exporting generated PDFs to public Download directories uses `ContentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)`, receiving a managed `content://` URI without needing broad storage permissions.
   - **Storage Access Framework (SAF)**: Importing external PDFs uses the system document picker (`OpenDocument`). The app secures durable access across device reboots by calling `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.

---

## 🧪 Testing Strategy & CI/CD Pipeline

DocumentsApp maintains an automated testing framework covering unit logic, image processing math, navigation graphs, and UI fragment lifecycles.

```
                   ┌───> [Unit Tests (JUnit 4)]
                   │
[./gradlew runAllTests] ┼───> [Robolectric Fragment & Nav Tests]
                   │
                   └───> [BouncyCastle / SVG Utils Math Tests]
```

### Test Suite Structure (`app/src/test/java/com/example/documentsapp/`)

* **`NavigationFlowTest.kt`**: Tests navigation stack transitions across all 11 destinations.
* **`PageNavigationTest.kt`**: Validates page reordering, insertion, and deletion logic in `DocumentViewModel`.
* **`DocumentViewModelTest.kt`**: Tests state flow, retake index management, and clear functions.
* **`DocumentModelTest.kt`**: Tests data model serialization and edge cases.
* **`P12GeneratorTest.kt`**: Verifies cryptographic PKCS#12 certificate generation and keystore password encryption.
* **`SvgExporterTest.kt`**: Tests SVG XML output structure and stroke path serialization.
* **`SvgUtilsTest.kt`**: Tests SVG vector rendering back to Bitmaps.

### Custom Gradle Task
Run all verification tasks with colored CLI execution summaries:
```bash
./gradlew runAllTests
```

### Continuous Integration (`.github/workflows/android.yml`)
Automated GitHub Actions workflow executes build and unit test steps on every commit and pull request.

---

## 🛡️ Security, Privacy & Scoped Storage

1. **100% On-Device Processing**: All image processing, edge detection contour calculations, SVG rendering, and cryptographic signing take place locally on the user's device without cloud server transmission.
2. **Password Encryption**: PKCS#12 digital certificate keystores are protected with custom passwords.
3. **Scoped Storage & FileProvider**: File sharing utilizes Android `FileProvider` with explicit `FLAG_GRANT_READ_URI_PERMISSION` flags.
