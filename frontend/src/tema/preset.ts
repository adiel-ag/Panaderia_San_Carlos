import { definePreset } from '@primeuix/themes'
import Aura from '@primeuix/themes/aura'

export const presetCorteza = definePreset(Aura, {
  primitive: { borderRadius: { md: '10px', xl: '20px' } },
  semantic: {
    primary: {
      50: '#FFF8EE', 100: '#F8E8D2', 200: '#F0D3B0', 300: '#E5B787',
      400: '#D9964F', 500: '#C47734', 600: '#9A4A1C', 700: '#7A3812',
      800: '#5C2B12', 900: '#3A2417', 950: '#26150A',
    },
    colorScheme: {
      light: {
        formField: { background: '#FFFFFF', color: '#3A2417', borderColor: '#E3D2B8' },
        primary: { color: '#9A4A1C', contrastColor: '#FFF8EE', hoverColor: '#7A3812', activeColor: '#5C2B12' },
        text: { color: '#3A2417', hoverColor: '#3A2417', mutedColor: '#6E5443', hoverMutedColor: '#5C4030' },
        surface: {
          0: '#FFFBF3', 50: '#F6EEDF', 100: '#F3E4CC', 200: '#E3D2B8',
          300: '#D9C4A7', 400: '#B99D7D', 500: '#957659', 600: '#6E5443',
          700: '#5C4030', 800: '#4A2C1A', 900: '#3A2417', 950: '#26150A',
        },
      },
      dark: {
        formField: { background: '#1F1611', color: '#F4E6D2', borderColor: '#43322A' },
        primary: { color: '#E39A55', contrastColor: '#26150A', hoverColor: '#F2B877', activeColor: '#F4D49A' },
        text: { color: '#F4E6D2', hoverColor: '#F4E6D2', mutedColor: '#C9B39B', hoverMutedColor: '#E7D6BF' },
        surface: {
          0: '#F4E6D2', 50: '#E7D6BF', 100: '#D9C4A7', 200: '#C9B39B',
          300: '#B69D85', 400: '#A78C76', 500: '#816755', 600: '#644C3F',
          700: '#43322A', 800: '#30221A', 900: '#271B14', 950: '#1F1611',
        },
      },
    },
  },
})
