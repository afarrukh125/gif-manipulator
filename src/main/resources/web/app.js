const DEFAULTS = {
    reverse: false,
    boomerang: false,
    loop: true,
    startFrame: 0,
    endFrame: -1,
    frameStep: 1,
    startOffset: 0,
    speed: 1,
    frameDelayMs: 0,
    width: 0,
    height: 0,
    cropX: 0,
    cropY: 0,
    cropWidth: 0,
    cropHeight: 0,
    rotation: 0,
    flipHorizontal: false,
    flipVertical: false,
    grayscale: false,
    invert: false,
    brightness: 1,
    contrast: 1,
    saturation: 1,
};

const $ = (id) => document.getElementById(id);
const bound = () => document.querySelectorAll("[data-opt]");

let options = { ...DEFAULTS };
let source = null;
let renderedUrl = null;
let originalUrl = null;
let pending = null;
let renderTimer = null;

let IDLE_HINT = $("url-status").textContent;

/** Which video formats this machine can read depends on whether the server found an ffmpeg, so it is asked. */
async function describeReach() {
    try {
        const response = await fetch("/api/capabilities");
        if (!response.ok) return;
        const { ffmpeg } = await response.json();
        IDLE_HINT = ffmpeg
            ? "Straight from the web. A GIF, MP4 or WebM link is converted behind the scenes."
            : "Straight from the web. MP4 links are converted too; WebM needs ffmpeg on the server's PATH.";
        if (!$("url-input").disabled) $("url-status").textContent = IDLE_HINT;
    } catch (e) {
        // Leaving the wording as it is beats replacing it with an error nobody can act on.
    }
}

/* Opening a source */

const MEDIA_NAME = /\.(gif|mp4|m4v|mov|webm|mkv|avi)$/i;
const LINK = /^https?:\/\//i;

function isMedia(file) {
    return MEDIA_NAME.test(file.name) || file.type === "image/gif" || file.type.startsWith("video/");
}

function load(file) {
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    return openSource(() => fetch("/api/gifs", { method: "POST", body }), busyTextFor(file.name, file.type));
}

function loadUrl(url) {
    const address = (url || "").trim();
    if (!address) return;
    return openSource(
        () => fetch("/api/gifs/url", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ url: address }),
        }),
        busyTextFor(address, ""),
    );
}

function busyTextFor(name, type) {
    return type.startsWith("video/") || /\.(mp4|m4v|mov|webm|mkv|avi)(\?|#|$)/i.test(name)
        ? "Turning that video into a GIF, this can take a few seconds…"
        : "Opening…";
}

async function openSource(send, busyText) {
    showError(null);
    setBusy(busyText);
    try {
        let response;
        try {
            response = await send();
        } catch (e) {
            showError("Could not reach the server. Is it still running?");
            return;
        }
        if (!response.ok) {
            showError(await errorText(response));
            return;
        }
        adopt(await response.json());
    } finally {
        setBusy(null);
    }
}

function adopt(opened) {
    source = opened;
    options = { ...DEFAULTS };
    revoke();
    originalUrl = `/api/gifs/${source.id}`;

    selectTab($("view-tabs"), "result");
    $("landing").hidden = true;
    $("editor").hidden = false;
    $("change-file").hidden = false;
    $("url-input").value = "";
    $("source-name").textContent = source.converted ? `${source.name} · from a video` : source.name;
    $("crop-image").src = originalUrl;

    applyBounds();
    writeControls();
    render();
}

function setBusy(message) {
    const status = $("url-status");
    status.textContent = message || IDLE_HINT;
    status.classList.toggle("working", Boolean(message));
    $("url-input").disabled = Boolean(message);
    $("url-go").disabled = Boolean(message);
}

/** Shows the picker again without throwing away what is already open, so a mis-click can be backed out of. */
function showLanding() {
    $("landing").hidden = false;
    $("editor").hidden = true;
    $("cancel-open").hidden = !source;
    $("url-input").focus();
}

function applyBounds() {
    const last = Math.max(0, source.frameCount - 1);
    for (const id of ["startFrame", "endFrame", "startOffset"]) {
        $(id).max = String(last);
    }
    $("endFrame").value = String(last);
    options.endFrame = last;
    $("frameStep").max = String(Math.max(1, Math.min(20, source.frameCount)));
    $("cropX").max = String(Math.max(0, source.width - 1));
    $("cropY").max = String(Math.max(0, source.height - 1));
    $("cropWidth").max = String(source.width);
    $("cropHeight").max = String(source.height);
}

/* Control binding */

function readControls() {
    for (const input of bound()) {
        const key = input.dataset.opt;
        options[key] = input.type === "checkbox" ? input.checked : numberOf(input);
    }
    if ($("lock-aspect").checked) {
        options.height = 0;
    }
    if (options.startFrame > options.endFrame) {
        options.startFrame = options.endFrame;
        $("startFrame").value = String(options.startFrame);
    }
}

function writeControls() {
    for (const input of bound()) {
        const value = options[input.dataset.opt];
        if (input.type === "checkbox") {
            input.checked = Boolean(value);
        } else {
            input.value = String(value);
        }
    }
    for (const chip of $("rotation-chips").children) {
        chip.classList.toggle("on", Number(chip.dataset.rotation) === options.rotation);
    }
    syncLabels();
}

function numberOf(input) {
    const value = Number(input.value);
    return Number.isFinite(value) ? value : 0;
}

function syncLabels() {
    $("speed-out").textContent = `${options.speed.toFixed(2)}×`;
    $("delay-out").textContent = options.frameDelayMs === 0 ? "original" : `${options.frameDelayMs} ms`;
    $("step-out").textContent = options.frameStep === 1 ? "frame" : `${ordinal(options.frameStep)} frame`;
    $("offset-out").textContent = String(options.startOffset);
    $("brightness-out").textContent = options.brightness.toFixed(2);
    $("contrast-out").textContent = options.contrast.toFixed(2);
    $("saturation-out").textContent = options.saturation.toFixed(2);

    const last = source ? source.frameCount - 1 : 0;
    const whole = options.startFrame === 0 && options.endFrame >= last;
    $("range-out").textContent = whole ? "all" : `${options.startFrame}–${options.endFrame}`;

    // 0 means "derive it", which reads better as an empty box against an "auto" placeholder than as a literal zero.
    const locked = $("lock-aspect").checked;
    $("height").disabled = locked;
    if (locked || options.height === 0) $("height").value = "";
    if (options.width === 0) $("width").value = "";
}

function ordinal(n) {
    const suffix = n % 10 === 1 && n % 100 !== 11 ? "st" : n % 10 === 2 && n % 100 !== 12 ? "nd" : n % 10 === 3 && n % 100 !== 13 ? "rd" : "th";
    return `${n}${suffix}`;
}

/* Rendering */

function scheduleRender() {
    readControls();
    syncLabels();
    drawCropBox();
    clearTimeout(renderTimer);
    renderTimer = setTimeout(render, 220);
}

async function render() {
    if (!source) return;
    if (pending) pending.abort();
    const controller = new AbortController();
    pending = controller;
    $("spinner").hidden = false;

    let response;
    try {
        response = await fetch(`/api/gifs/${source.id}/render`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(options),
            signal: controller.signal,
        });
    } catch (e) {
        if (e.name !== "AbortError") showError("Could not reach the server. Is it still running?");
        return;
    } finally {
        if (pending === controller) {
            pending = null;
            $("spinner").hidden = true;
        }
    }

    if (!response.ok) {
        showError(await errorText(response));
        return;
    }
    showError(null);

    const blob = await response.blob();
    if (renderedUrl) URL.revokeObjectURL(renderedUrl);
    renderedUrl = URL.createObjectURL(blob);
    if (currentView() === "result") $("preview").src = renderedUrl;

    $("download").disabled = false;
    $("stat-size").textContent = `${response.headers.get("X-Gif-Width")} × ${response.headers.get("X-Gif-Height")}`;
    $("stat-frames").textContent = response.headers.get("X-Gif-Frames");
    $("stat-duration").textContent = formatDuration(Number(response.headers.get("X-Gif-Duration-Ms")));
    $("stat-bytes").textContent = formatBytes(blob.size);
}

async function errorText(response) {
    try {
        const body = await response.json();
        return body.error || `Request failed (${response.status})`;
    } catch (e) {
        return `Request failed (${response.status})`;
    }
}

function showError(message) {
    const box = $("error");
    box.hidden = !message;
    box.textContent = message || "";
}

function formatDuration(ms) {
    return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`;
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function selectTab(group, view) {
    for (const tab of group.children) {
        tab.classList.toggle("on", tab.dataset.view === view);
    }
}

function currentView() {
    return $("view-tabs").querySelector(".on").dataset.view;
}

function revoke() {
    if (renderedUrl) URL.revokeObjectURL(renderedUrl);
    renderedUrl = null;
}

/* Crop overlay */

function cropScale() {
    const image = $("crop-image");
    return image.clientWidth > 0 && source ? image.clientWidth / source.width : 1;
}

function drawCropBox() {
    const box = $("crop-box");
    if (!options.cropWidth || !options.cropHeight) {
        box.hidden = true;
        return;
    }
    const scale = cropScale();
    box.hidden = false;
    box.style.left = `${options.cropX * scale}px`;
    box.style.top = `${options.cropY * scale}px`;
    box.style.width = `${options.cropWidth * scale}px`;
    box.style.height = `${options.cropHeight * scale}px`;
}

function setCrop(x, y, width, height) {
    options.cropX = Math.round(x);
    options.cropY = Math.round(y);
    options.cropWidth = Math.round(width);
    options.cropHeight = Math.round(height);
    for (const key of ["cropX", "cropY", "cropWidth", "cropHeight"]) {
        $(key).value = String(options[key]);
    }
    drawCropBox();
}

function wireCrop() {
    const stage = $("crop-stage");
    const box = $("crop-box");
    let drag = null;

    const pointAt = (event) => {
        const rect = $("crop-image").getBoundingClientRect();
        const scale = cropScale();
        return {
            x: clamp((event.clientX - rect.left) / scale, 0, source.width),
            y: clamp((event.clientY - rect.top) / scale, 0, source.height),
        };
    };

    stage.addEventListener("pointerdown", (event) => {
        if (!source) return;
        stage.setPointerCapture(event.pointerId);
        const start = pointAt(event);
        drag = event.target === box
            ? { mode: "move", start, origin: { x: options.cropX, y: options.cropY } }
            : { mode: "draw", start };
        if (drag.mode === "draw") setCrop(start.x, start.y, 0, 0);
        event.preventDefault();
    });

    stage.addEventListener("pointermove", (event) => {
        if (!drag) return;
        const at = pointAt(event);
        if (drag.mode === "draw") {
            setCrop(
                Math.min(drag.start.x, at.x),
                Math.min(drag.start.y, at.y),
                Math.abs(at.x - drag.start.x),
                Math.abs(at.y - drag.start.y),
            );
        } else {
            const maxX = source.width - options.cropWidth;
            const maxY = source.height - options.cropHeight;
            setCrop(
                clamp(drag.origin.x + at.x - drag.start.x, 0, maxX),
                clamp(drag.origin.y + at.y - drag.start.y, 0, maxY),
                options.cropWidth,
                options.cropHeight,
            );
        }
    });

    const finish = () => {
        if (!drag) return;
        drag = null;
        // A stray click without a drag would otherwise leave a zero sized crop that hides the box but keeps the numbers.
        if (options.cropWidth < 2 || options.cropHeight < 2) setCrop(0, 0, 0, 0);
        scheduleRender();
    };

    stage.addEventListener("pointerup", finish);
    stage.addEventListener("pointercancel", finish);
}

/** The width a scale preset multiplies: what the frame measures after cropping and rotating, before any resize. */
function scaleBase() {
    const turned = options.rotation === 90 || options.rotation === 270;
    if (options.cropWidth > 0 && options.cropHeight > 0) {
        return turned ? options.cropHeight : options.cropWidth;
    }
    return turned ? source.height : source.width;
}

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

/* Wiring */

function wire() {
    $("file-input").addEventListener("change", (e) => load(e.target.files[0]));
    $("change-file").addEventListener("click", showLanding);
    $("cancel-open").addEventListener("click", () => {
        $("landing").hidden = true;
        $("editor").hidden = false;
    });

    $("url-form").addEventListener("submit", (event) => {
        event.preventDefault();
        loadUrl($("url-input").value);
    });

    for (const input of bound()) {
        input.addEventListener("input", scheduleRender);
    }

    $("lock-aspect").addEventListener("change", scheduleRender);

    $("rotation-chips").addEventListener("click", (event) => {
        const chip = event.target.closest("[data-rotation]");
        if (!chip) return;
        options.rotation = Number(chip.dataset.rotation);
        for (const other of $("rotation-chips").children) {
            other.classList.toggle("on", other === chip);
        }
        scheduleRender();
    });

    $("scale-presets").addEventListener("click", (event) => {
        const chip = event.target.closest("[data-scale]");
        if (!chip || !source) return;
        const scale = Number(chip.dataset.scale);
        $("lock-aspect").checked = true;
        // 100% means leave the size alone. Pinning an explicit width instead would freeze the output at today's
        // dimensions, so a later rotation or crop would be stretched back to that width.
        $("width").value = scale === 1 ? "0" : String(Math.max(1, Math.round(scaleBase() * scale)));
        scheduleRender();
    });

    $("clear-crop").addEventListener("click", () => {
        setCrop(0, 0, 0, 0);
        scheduleRender();
    });

    $("view-tabs").addEventListener("click", (event) => {
        const tab = event.target.closest("[data-view]");
        if (!tab) return;
        selectTab($("view-tabs"), tab.dataset.view);
        $("preview").src = (tab.dataset.view === "original" ? originalUrl : renderedUrl) || "";
    });

    // The crop image has no measurable width while its section is collapsed, so the box can only be placed on open.
    $("crop-group").addEventListener("toggle", drawCropBox);
    $("crop-image").addEventListener("load", drawCropBox);

    $("reset").addEventListener("click", () => {
        options = { ...DEFAULTS };
        $("lock-aspect").checked = true;
        applyBounds();
        writeControls();
        drawCropBox();
        render();
    });

    $("download").addEventListener("click", () => {
        if (!renderedUrl) return;
        const link = document.createElement("a");
        link.href = renderedUrl;
        link.download = source.name.replace(/\.gif$/i, "") + "-edited.gif";
        link.click();
    });

    wireCrop();
    wireDropTarget();
    wirePaste();
    describeReach();
    window.addEventListener("resize", drawCropBox);
}

function wireDropTarget() {
    const overlay = $("drop-overlay");
    let depth = 0;

    window.addEventListener("dragenter", (event) => {
        event.preventDefault();
        depth++;
        overlay.hidden = false;
    });
    window.addEventListener("dragover", (event) => event.preventDefault());
    window.addEventListener("dragleave", (event) => {
        event.preventDefault();
        // dragleave also fires when the pointer crosses into a child element, so only hide once every enter is matched.
        if (--depth <= 0) {
            depth = 0;
            overlay.hidden = true;
        }
    });
    window.addEventListener("drop", (event) => {
        event.preventDefault();
        depth = 0;
        overlay.hidden = true;
        const file = [...(event.dataTransfer?.files || [])].find(isMedia);
        if (file) {
            load(file);
            return;
        }
        // Dragging an image out of another tab hands over its address rather than its bytes.
        const link = (event.dataTransfer?.getData("text/uri-list") || event.dataTransfer?.getData("text") || "").trim();
        if (LINK.test(link)) {
            loadUrl(link);
        } else if (event.dataTransfer?.files.length) {
            showError("That was not a GIF or a video.");
        }
    });
}

function wirePaste() {
    window.addEventListener("paste", (event) => {
        if (event.target?.tagName === "INPUT" || event.target?.tagName === "TEXTAREA") return;
        const file = [...(event.clipboardData?.files || [])].find(isMedia);
        if (file) {
            load(file);
            return;
        }
        const text = (event.clipboardData?.getData("text") || "").trim();
        if (LINK.test(text)) loadUrl(text);
    });
}

wire();
