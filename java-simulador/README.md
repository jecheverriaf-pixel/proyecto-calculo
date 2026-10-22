# Emulador de Sólidos de Revolución

Proyecto de Cálculo II, Universidad Mariano Gálvez de Guatemala.

Calcula el volumen de un sólido de revolución con los métodos de **discos**, **arandelas** (alrededor del eje X) y **capas cilíndricas** (alrededor del eje Y), y además:

- valida los datos de entrada (sección 6 del enunciado);
- compara el volumen con el resultado manual (sección 8);
- dibuja la región plana en 2D, el sólido en 3D y la curva de volumen acumulado V_acum(x) (secciones 9 a 11);
- exporta los datos a CSV/TXT y las gráficas a PNG (secciones 12 y 17).

## Cómo ejecutarlo

### Opción 1: ejecutable de Windows (no requiere Java instalado)

Abrir `SimuladorSolidos\SimuladorSolidos.exe`. Hay que copiar la carpeta `SimuladorSolidos` **completa**, porque el `.exe` depende de las subcarpetas `app` y `runtime`.

Para volver a generar esa carpeta desde el código fuente:

```powershell
powershell -ExecutionPolicy Bypass -File empaquetar.ps1
```

El resultado queda en `target\dist\SimuladorSolidos\`.

### Opción 2: JAR (requiere Java 21)

```powershell
java -jar target\simulador-solidos-revolucion-1.0.0-shaded.jar
```

### Opción 3: desde el código fuente (requiere JDK 21 y Maven)

```powershell
mvn javafx:run      # ejecuta la aplicación
mvn test            # corre las pruebas unitarias
mvn package         # genera el JAR en target\
```

## Uso rápido

1. Escriba f(x) (función superior) y g(x) (función inferior). Se aceptan `x^2`, `sqrt(x)`, `sin(x)`, `exp(x)`, `log(x)`, `pi`, etc.
2. Ingrese los límites a y b, o presione **Calcular intersecciones f(x) = g(x)** para que el programa los encuentre resolviendo f(x) − g(x) = 0 en [−10, 10].
3. Elija el método. El eje se ajusta solo, y las combinaciones incompatibles se rechazan.
4. Escriba el volumen obtenido a mano. Se acepta `73.5133`, `73,5133` o la forma exacta `117*pi/5`.
5. Exporte los datos con **Exportar CSV/TXT** y las gráficas con **Exportar gráficas PNG**.

Los botones de la parte superior cargan el ejercicio principal y las tres configuraciones de validación de la sección 13.

## Formulación implementada

| Método | Eje | Volumen |
|---|---|---|
| Discos | X | V = π ∫ₐᵇ [f(x)]² dx |
| Arandelas | X | V = π ∫ₐᵇ ([f(x)]² − [g(x)]²) dx |
| Capas cilíndricas | Y | V = 2π ∫ₐᵇ x (f(x) − g(x)) dx |

Las integrales se calculan con la **regla de Simpson 1/3 compuesta** (n = 800 subintervalos). V_acum(x) es la misma integral evaluada desde a hasta x.

## Estructura del código

```
src/main/java/com/umg/calculo/
├── engine/
│   ├── FunctionEvaluator.java     interpreta f(x), g(x) y constantes (exp4j)
│   ├── VolumeCalculator.java      Simpson 1/3 e integrando de cada método
│   ├── Validator.java             reglas de validación (sección 6)
│   ├── IntersectionFinder.java    raíces de f(x) − g(x) (bisección)
│   └── RevolutionMeshBuilder.java malla 3D del sólido
├── export/
│   ├── DataExporter.java          CSV / TXT
│   └── ImageExporter.java         PNG
├── model/                         métodos, ejes y configuraciones predefinidas
└── ui/                            interfaz JavaFX (MainView, gráficas 2D y vista 3D)
```

## Validación (sección 13)

| Configuración | Funciones | Intervalo | Método | Volumen exacto |
|---|---|---|---|---|
| Principal | f = x + 3, g = x² + 1 | [−1, 2] | Arandelas (eje X) | 117π/5 ≈ 73.5133 u³ |
| 1 | f = √x, g = 0 | [0, 4] | Discos (eje X) | 8π ≈ 25.1327 u³ |
| 2 | f = √x, g = x² | [0, 1] | Arandelas (eje X) | 3π/10 ≈ 0.9425 u³ |
| 3 | f = 2x, g = x² | [0, 2] | Capas (eje Y) | 8π/3 ≈ 8.3776 u³ |

Las pruebas unitarias (`mvn test`) verifican estos cuatro volúmenes, la búsqueda de intersecciones y las reglas de validación.
