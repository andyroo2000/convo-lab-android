"""Apply the iOS Journal adapters to the exact pinned converter sources."""
import shutil
import subprocess

REVISION = '80b8204f1cdfb364bb2ed355cf52c7ebb2519a0c'
ZENZ = 'Sources/KanaKanjiConverterModule/ConversionAlgorithms/Zenzai/Zenz'


def original(vendor, name):
    return subprocess.check_output(
        ['git', 'show', f'{REVISION}:{ZENZ}/{name}.swift'], cwd=vendor, text=True)


def replace_required(source, before, after):
    if before not in source:
        raise RuntimeError(f'Pinned converter declaration changed: {before}')
    return source.replace(before, after)


def patch_typing(root, vendor):
    adapters = root / 'native/Adapters'
    api = vendor / 'Sources/KanaKanjiConverterModule/ConverterAPI'
    for name in ['WholePhraseCandidates', 'NeuralBoundaryRanker', 'BoundaryScoreCache']:
        shutil.copy2(adapters / f'{name}.swift', api / f'ConvoLab{name}.swift')
    # cpu_model.cpp already applies the CPU loader's required LAYER split.
    # Leave the pinned Swift declaration alone instead of patching it twice.
    context = original(vendor, 'ZenzContext')
    context = replace_required(context, 'min(8, ProcessInfo.processInfo.processorCount - 2)',
                               'min(4, ProcessInfo.processInfo.processorCount)')
    scoring = (adapters / 'BoundaryNormalization.swift').read_text()
    scoring += '\n' + (adapters / 'BoundaryBatchScoring.swift').read_text()
    scoring += '\n' + (adapters / 'BoundaryPrefixScoring.swift').read_text()
    scoring += '\n' + (adapters / 'BoundaryDiagnostics.swift').read_text()
    (vendor / ZENZ / 'ZenzContext.swift').write_text(context + '\n' + scoring)
    zenz = replace_required(original(vendor, 'Zenz'),
                            'private var zenzContext: ZenzContext?',
                            'var zenzContext: ZenzContext?')
    zenz = replace_required(zenz, 'try? self.zenzContext?.reset_context()',
                            'self.zenzContext?.resetForTyping()')
    (vendor / ZENZ / 'Zenz.swift').write_text(zenz)
