import DOMPurify from 'dompurify';

export function createSvgSanitizer(purifier) {
  purifier.addHook('uponSanitizeAttribute', (_, attribute) => {
    if (['href', 'xlink:href'].includes(attribute.attrName) && !attribute.attrValue.startsWith('#')) {
      attribute.keepAttr = false;
    }
  });
  return markup => {
    if (!markup || typeof markup !== 'string') return '';
    return purifier.sanitize(markup, {
      USE_PROFILES: { svg: true, svgFilters: true },
      FORBID_TAGS: ['script', 'foreignObject', 'style', 'animate', 'set'],
      FORBID_ATTR: ['style'],
      ALLOW_DATA_ATTR: false,
    });
  };
}

export const sanitizeSvg = typeof DOMPurify.sanitize === 'function'
  ? createSvgSanitizer(DOMPurify) : () => '';
