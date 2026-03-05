#version 300 es
precision mediump float;
in vec2 TexCoord;
out vec4 FragColor;
uniform sampler2D ourTexture;
void main() {
    FragColor = texture(ourTexture, TexCoord);
}