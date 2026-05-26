const fs = require('fs');

try {
    let code = fs.readFileSync('app/src/main/java/com/example/MainActivity.kt', 'utf8');

    code = code.replace(/Color\(0xFF00FF87\)/g, "Color.White");
    code = code.replace(/Color\(0xFFFF5252\)/g, "Color.White");
    code = code.replace(/Color\(0xFFFFD700\)/g, "Color.White");
    code = code.replace(/Color\(0xFF8B8A9D\)/g, "Color.Gray");
    code = code.replace(/Color\(0xFFBDC2E8\)/g, "Color.LightGray");

    code = code.replace(/Color\(0xFF([A-Fa-f0-9]{6})\)/g, (match, hex) => {
        const r = parseInt(hex.substring(0, 2), 16);
        const g = parseInt(hex.substring(2, 4), 16);
        const b = parseInt(hex.substring(4, 6), 16);
        const avg = (r + g + b) / 3;
        
        if (avg > 150) return "Color.LightGray";
        if (avg > 80) return "Color.Gray";
        if (avg > 30) return "Color.DarkGray";
        return "Color.Black";
    });

    fs.writeFileSync('app/src/main/java/com/example/MainActivity.kt', code);
    console.log("Colors replaced successfully.");
} catch(e) {
    console.error(e);
}
