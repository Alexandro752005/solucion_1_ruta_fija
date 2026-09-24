import 'package:flutter/material.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_announcement_repository.dart';
import '../domain/mobile_announcement_models.dart';
import 'mobile_announcement_presentation.dart';

class AnnouncementDetailPage extends StatefulWidget {
  const AnnouncementDetailPage({
    required this.announcementId,
    required this.announcementGateway,
    super.key,
  });

  final String announcementId;
  final MobileAnnouncementGateway announcementGateway;

  @override
  State<AnnouncementDetailPage> createState() => _AnnouncementDetailPageState();
}

class _AnnouncementDetailPageState extends State<AnnouncementDetailPage> {
  MobileAnnouncement? _announcement;
  Object? _error;
  bool _loading = true;
  bool _markingRead = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final announcement = await widget.announcementGateway.get(
        widget.announcementId,
      );
      if (mounted) {
        setState(() => _announcement = announcement);
      }
    } catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _confirmRead() async {
    final announcement = _announcement;
    if (announcement == null ||
        !announcement.requiresReadConfirmation ||
        _markingRead) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Confirmar lectura'),
        content: const Text(
          'El servidor registrará que usted confirmó este comunicado. Esta acción puede repetirse de forma segura si la red falla.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Confirmar lectura'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }

    setState(() => _markingRead = true);
    try {
      final result = await widget.announcementGateway.markRead(announcement.id);
      if (!mounted) {
        return;
      }
      setState(() {
        _announcement = announcement.withReadAt(result.readAt);
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Lectura confirmada por el servidor.')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is MobileNetworkException
          ? 'No se pudo confirmar por la red. Puede reintentar de forma segura.'
          : mobileErrorMessage(error);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) {
        setState(() => _markingRead = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Comunicado')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.cloud_off_outlined, size: 40),
                const SizedBox(height: 16),
                Text(mobileErrorMessage(_error!), textAlign: TextAlign.center),
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: _load,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Reintentar'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final announcement = _announcement!;
    return Scaffold(
      appBar: AppBar(title: const Text('Comunicado')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                announcement.title,
                style: Theme.of(context).textTheme.headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(announcement.body),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _DetailRow(
                        'Publicado',
                        announcementDateTimeLabel(announcement.createdAt),
                      ),
                      if (announcement.deliveredAt != null)
                        _DetailRow(
                          'Entregado',
                          announcementDateTimeLabel(announcement.deliveredAt!),
                        ),
                      if (announcement.readAt != null)
                        _DetailRow(
                          'Lectura confirmada',
                          announcementDateTimeLabel(announcement.readAt!),
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              if (announcement.requiresReadConfirmation)
                Card(
                  color: Theme.of(context).colorScheme.tertiaryContainer,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Este comunicado requiere su confirmación.',
                          style: Theme.of(context).textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'Abrirlo no registra una lectura. Use el botón cuando haya revisado el contenido.',
                        ),
                        const SizedBox(height: 16),
                        FilledButton.icon(
                          key: const Key('announcement-confirm-read'),
                          onPressed: _markingRead ? null : _confirmRead,
                          icon: _markingRead
                              ? const SizedBox(
                                  height: 18,
                                  width: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.mark_email_read_outlined),
                          label: Text(
                            _markingRead ? 'Confirmando…' : 'Confirmar lectura',
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else
                Card(
                  color: Theme.of(context).colorScheme.secondaryContainer,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      announcement.readAt == null
                          ? 'Este comunicado es informativo y no requiere confirmación.'
                          : 'El servidor ya registra su lectura.',
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 2),
          Text(value),
        ],
      ),
    );
  }
}
