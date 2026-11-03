// Generation de QR : charge le script embarque (/vendor/qrcode/qrcode.js, MIT) puis dessine une matrice en SVG.
const LIBRARY_URL = '/vendor/qrcode/qrcode.js';
const MAX_TYPE = 40;
/** Zone de silence en modules : 1 suffit sur fond sombre et garde le QR petit. */
export const QR_MARGIN = 1;

let libraryPromise = null;

/** Charge une seule fois le script QR et renvoie `{ QRCode, QRErrorCorrectLevel }`. */
export function loadQrLibrary() {
  if (globalThis.AssistantQr) {
    return Promise.resolve(globalThis.AssistantQr);
  }
  if (!libraryPromise) {
    libraryPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = LIBRARY_URL;
      script.onload = () => (globalThis.AssistantQr
        ? resolve(globalThis.AssistantQr)
        : reject(new Error('Le générateur de QR est indisponible.')));
      script.onerror = () => {
        libraryPromise = null;
        reject(new Error('Impossible de charger le générateur de QR.'));
      };
      document.head.append(script);
    });
  }
  return libraryPromise;
}

/** Construit le plus petit QR (correction d'erreur L : l'ecran est net et le QR reste compact) qui contient le texte. */
export function buildQr(library, text) {
  for (let type = 1; type <= MAX_TYPE; type += 1) {
    try {
      const qr = new library.QRCode(type, library.QRErrorCorrectLevel.L);
      qr.addData(text);
      qr.make();
      return qr;
    } catch (error) {
      if (type === MAX_TYPE) {
        throw new Error('Texte trop long pour un QR.');
      }
    }
  }
  throw new Error('Texte trop long pour un QR.');
}

function escapeXml(value) {
  return String(value).replace(/[<>&"']/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' })[c]);
}

/** SVG redimensionnable (fond blanc, zone de silence de `margin` modules) a partir d'un QR construit. */
export function qrSvg(qr, { margin = QR_MARGIN, label = 'QR code' } = {}) {
  const count = qr.getModuleCount();
  const size = count + margin * 2;
  let path = '';
  for (let row = 0; row < count; row += 1) {
    for (let col = 0; col < count; col += 1) {
      if (qr.isDark(row, col)) {
        path += `M${col + margin},${row + margin}h1v1h-1z`;
      }
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" role="img" `
    + `aria-label="${escapeXml(label)}" shape-rendering="crispEdges">`
    + `<rect width="${size}" height="${size}" fill="#fff"/><path d="${path}" fill="#000"/></svg>`;
}

export async function renderQr(container, text, options) {
  const library = await loadQrLibrary();
  container.innerHTML = qrSvg(buildQr(library, text), options);
}
