// by Claude - Updated to read ports from environment variables
import { defineConfig } from 'vite'

// Read ports from environment or use defaults
const frontendPort = parseInt(process.env.FRONTEND_PORT || '8941')
const backendPort = parseInt(process.env.BACKEND_PORT || '8081')

export default defineConfig({
    root: "kotlin",
    server: {
        host: true,
        port: frontendPort,
        allowedHosts: ["*"],
        proxy: {
            '/api': {
                target: `http://localhost:${backendPort}`,
                rewrite: (path) => path.replace(/^\/api/, ''),
                ws: true,
            }
        }
    },
    // by Claude - barcode-detector uses named exports but KiteUI imports it as default.
    // This plugin rewrites the default import to use the named BarcodeDetector export.
    plugins: [{
        name: 'barcode-detector-default-export',
        transform(code, id) {
            if (id.includes('CameraPreview') && code.includes("import BarcodeDetector from 'barcode-detector'")) {
                return code.replace(
                    "import BarcodeDetector from 'barcode-detector'",
                    "import { BarcodeDetector } from 'barcode-detector'"
                )
            }
        }
    }],
})
