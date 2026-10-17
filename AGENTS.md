# Repository instructions

## Code style

1. Declare all fields together at the top of each class, before constructors and methods. Use immutable classes with explicit fields rather than records for domain objects and response DTOs. Request DTOs may be mutable for Spring binding.
2. Use explicit imports, including static imports; do not use wildcard imports.
3. Place custom exceptions in the `com.example.carrental.exception` package.
4. Give every test assertion a message identifying the design requirement it checks. Make the scenario explicit so the message is easy to interpret.

