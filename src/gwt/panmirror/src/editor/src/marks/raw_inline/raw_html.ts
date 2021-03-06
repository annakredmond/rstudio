/*
 * raw_html.ts
 *
 * Copyright (C) 2020 by RStudio, PBC
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

import { Mark, Schema, Fragment } from 'prosemirror-model';
import { InputRule } from 'prosemirror-inputrules';
import { toggleMark } from 'prosemirror-commands';
import { EditorState } from 'prosemirror-state';

import { setTextSelection } from 'prosemirror-utils';

import { PandocExtensions, PandocTokenType, PandocToken, ProsemirrorWriter, PandocOutput } from '../../api/pandoc';
import { Extension } from '../../api/extension';
import { isRawHTMLFormat, kHTMLFormat } from '../../api/raw';
import { EditorUI } from '../../api/ui';
import { EditorCommandId } from '../../api/command';
import { PandocCapabilities } from '../../api/pandoc_capabilities';
import { MarkInputRuleFilter } from '../../api/input_rule';

import { kRawInlineFormat, kRawInlineContent, RawInlineCommand } from './raw_inline';

import { InsertHTMLCommentCommand } from './raw_html_comment';
import { fancyQuotesToSimple } from '../../api/quote';
const extension = (pandocExtensions: PandocExtensions, pandocCapabilities: PandocCapabilities): Extension | null => {
  return {
    marks: [
      {
        name: 'raw_html',
        noInputRules: true,
        spec: {
          inclusive: false,
          excludes: '_',
          parseDOM: [
            {
              tag: "span[class*='raw-html']",
              getAttrs(dom: Node | string) {
                return {};
              },
            },
          ],
          toDOM(mark: Mark) {
            const attr: any = {
              class: 'raw-html pm-fixedwidth-font pm-markup-text-color',
            };
            return ['span', attr];
          },
        },
        pandoc: {
          readers: [
            {
              token: PandocTokenType.RawInline,
              match: (tok: PandocToken) => {
                const format = tok.c[kRawInlineFormat];
                return isRawHTMLFormat(format);
              },
              handler: (_schema: Schema) => {
                return (writer: ProsemirrorWriter, tok: PandocToken) => {
                  const text = tok.c[kRawInlineContent];
                  writer.writeInlineHTML(text);
                };
              },
            },
          ],
          writer: {
            priority: 20,
            write: (output: PandocOutput, _mark: Mark, parent: Fragment) => {
              output.writeRawMarkdown(parent);
            },
          },
        },
      },
    ],

    // insert command
    commands: (schema: Schema, ui: EditorUI) => {
      const commands = [new InsertHTMLCommentCommand(schema)];
      if (pandocExtensions.raw_html) {
        commands.push(
          new RawInlineCommand(EditorCommandId.HTMLInline, kHTMLFormat, ui, pandocCapabilities.output_formats),
        );
      }
      return commands;
    },

    // input rules
    inputRules: (schema: Schema, filter: MarkInputRuleFilter) => {
      if (pandocExtensions.raw_html) {
        return [rawHtmlInputRule(schema, filter)];
      } else {
        return [];
      }
    },
  };
};

export function rawHtmlInputRule(schema: Schema, filter: MarkInputRuleFilter) {
  return new InputRule(/>$/, (state: EditorState, match: string[], start: number, end: number) => {
    const rawhtmlMark = state.schema.marks.raw_html;

    // ensure we pass all conditions for html input
    if (state.selection.empty && toggleMark(rawhtmlMark)(state) && filter(state, start, end)) {
      // get tag info
      const { parent, parentOffset } = state.selection.$head;
      const text = parent.textContent;
      const endLoc = parentOffset - 1;
      const tag = tagInfo(text, endLoc);
      if (tag) {
        // create transaction
        const tr = state.tr;

        // insert >
        tr.insertText('>');

        // add mark
        start = tr.selection.from - (tag.end - tag.start + 1);
        tr.addMark(start, end + 1, rawhtmlMark.create());
        tr.removeStoredMark(rawhtmlMark);

        // if it wasn't an end tag and it isn't a void tag then also
        // insert an end tag (and leave the cursor in the middle)
        if (!tag.close && !tag.void) {
          const endTag = schema.text(`</${tag.name}>`);
          tr.replaceSelectionWith(endTag, false);
          setTextSelection(tr.selection.from - endTag.textContent.length)(tr);
          tr.addMark(tr.selection.from, tr.selection.from + endTag.textContent.length, rawhtmlMark.create());
          tr.removeStoredMark(rawhtmlMark);
        }

        // return transaction
        return tr;
      }
    }

    return null;
  });
}

function tagInfo(text: string, endLoc: number) {
  const startLoc = tagStartLoc(text, endLoc);
  if (startLoc !== -1) {
    // don't match if preceding character is a backtick 
    // (user is attempting to write an html tag in code)
    if (text.charAt(startLoc-1) === '`') {
      return null;
    }
    const tagText = text.substring(startLoc, endLoc + 1);
    const match = tagText.match(/<(\/?)(\w+)/);
    if (match) {
      const name = match[2];
      if (isHTMLTag(name)) {
        return {
          name: match[2],
          close: match[1].length > 0,
          void: isVoidTag(name),
          start: startLoc,
          end: endLoc + 1,
        };
      }
    }
  }
  return null;
}

function tagStartLoc(text: string, endLoc: number) {
  // might be smart quotes
  text = fancyQuotesToSimple(text);

  let inSingleQuote = false;
  let inDoubleQuote = false;
  let i;
  for (i = endLoc; i >= 0; i--) {
    // next character
    const ch = text[i];

    // invalid if we see another > when not in quotes
    if (ch === '>' && !inSingleQuote && !inDoubleQuote) {
      return -1;
    }

    // > terminate on < if we aren't in quotes
    if (ch === '<' && !inSingleQuote && !inDoubleQuote) {
      return i;
    }

    // handle single quote
    if (ch === "'") {
      if (inSingleQuote) {
        inSingleQuote = false;
      } else if (!inDoubleQuote) {
        inSingleQuote = true;
      }

      // handle double quote
    } else if (ch === '"') {
      if (inDoubleQuote) {
        inDoubleQuote = false;
      } else if (!inSingleQuote) {
        inDoubleQuote = true;
      }
    }
  }

  return -1;
}

function isHTMLTag(tag: string) {
  return [
    // structural
    'a',
    'article',
    'aside',
    'body',
    'br',
    'details',
    'div',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'head',
    'header',
    'hgroup',
    'hr',
    'html',
    'footer',
    'nav',
    'p',
    'section',
    'span',
    'summary',

    // metadata
    'base',
    'basefont',
    'link',
    'meta',
    'style',
    'title',

    // form
    'button',
    'datalist',
    'fieldset',
    'form',
    'input',
    'keygen',
    'label',
    'legend',
    'meter',
    'optgroup',
    'option',
    'select',
    'textarea',

    // formatting
    'abbr',
    'acronym',
    'address',
    'b',
    'bdi',
    'bdo',
    'big',
    'blockquote',
    'center',
    'cite',
    'code',
    'del',
    'dfn',
    'em',
    'font',
    'i',
    'ins',
    'kbd',
    'mark',
    'output',
    'pre',
    'progress',
    'q',
    'rp',
    'rt',
    'ruby',
    's',
    'samp',
    'small',
    'strike',
    'strong',
    'sub',
    'sup',
    'tt',
    'u',
    'var',
    'wbr',

    // list
    'dd',
    'dir',
    'dl',
    'dt',
    'li',
    'ol',
    'menu',
    'ul',

    // table
    'caption',
    'col',
    'colgroup',
    'table',
    'tbody',
    'td',
    'tfoot',
    'thead',
    'th',
    'tr',

    // scripting
    'script',
    'noscript',

    // embedded content
    'applet',
    'area',
    'audio',
    'canvas',
    'embed',
    'figcaption',
    'figure',
    'frame',
    'frameset',
    'iframe',
    'img',
    'map',
    'noframes',
    'object',
    'param',
    'source',
    'time',
    'video',
  ].includes(tag.toLowerCase());
}

function isVoidTag(tag: string) {
  return [
    'area',
    'base',
    'br',
    'col',
    'command',
    'embed',
    'hr',
    'img',
    'input',
    'keygen',
    'link',
    'meta',
    'param',
    'source',
    'track',
    'wbr',
  ].includes(tag.toLowerCase());
}

export default extension;
